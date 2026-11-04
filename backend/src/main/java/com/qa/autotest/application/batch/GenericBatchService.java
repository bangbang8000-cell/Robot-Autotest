package com.qa.autotest.application.batch;

import com.qa.autotest.application.asset.CredentialService;
import com.qa.autotest.application.asset.DeviceService;
import com.qa.autotest.application.asset.LinkService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.iam.UserService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.iam.UserSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 通用列表批量执行器（UX-02b / WBS #71 / FR-v0.2-40）。
 *
 * <p>与 {@code TestCaseService.batch()} <b>同一套契约</b>（DEV-PLAN §10.5 硬规则）：
 * 部分成功返回 200、逐条独立事务、单条失败不影响其他、逐条审计带 {@code batch=} 标识，
 * 只有参数级错误（ids 空 / &gt;200 / 资源或动作未知 / 动作专属参数缺失）才 400。
 * 资产 / 用户三页复用它，不各写一套批量实现。
 *
 * <p><b>逐条事务的正确姿势</b>：异常必须冒泡出 {@link TransactionTemplate} 才能让该条回滚，
 * 因此 catch 放在事务外；若在 lambda 内 catch 并转成结果，事务会照常提交，造成半写入。
 *
 * <p><b>范围边界</b>：只编排<b>已存在</b>的单条业务操作，不新增业务能力。报告域的列表主键是
 * {@code runId} 字符串、导出是同步生成文件的任务型操作，与 {@code ids: List<Long>} 契约不兼容，
 * 本包不接入报告页（登记为已知边界，需要时另开包）。
 */
@Service
public class GenericBatchService {

    public static final Set<String> RESOURCES = Set.of("DEVICE", "LINK", "CREDENTIAL", "USER");
    public static final Set<String> ACTIONS = Set.of("delete", "setEnabled");
    public static final int BATCH_MAX = 200;

    /** 批量请求：{@code enabled} 仅 USER + setEnabled 必填。 */
    public record BatchRequest(String resource, String action, List<Long> ids, Boolean enabled) {
    }

    /** 逐条结果：失败带稳定错误码名。 */
    public record BatchItemResult(long id, boolean ok, String code, String message) {
    }

    /** 批量结果：部分成功语义。 */
    public record BatchResult(int total, int succeeded, int failed, List<BatchItemResult> items) {
    }

    private final DeviceService devices;
    private final LinkService links;
    private final CredentialService credentials;
    private final UserService users;
    private final AuditService audit;
    private final TransactionTemplate tx;

    /** Spring 装配入口：批量走逐条独立事务。 */
    @Autowired
    public GenericBatchService(DeviceService devices, LinkService links,
                               CredentialService credentials, UserService users,
                               AuditService audit, PlatformTransactionManager txManager) {
        this(devices, links, credentials, users, audit, new TransactionTemplate(txManager));
    }

    /** 测试装配入口：可传 null 事务（逐条直接执行），也可传真事务模板验证回滚。 */
    public GenericBatchService(DeviceService devices, LinkService links,
                               CredentialService credentials, UserService users,
                               AuditService audit, TransactionTemplate tx) {
        this.devices = devices;
        this.links = links;
        this.credentials = credentials;
        this.users = users;
        this.audit = audit;
        this.tx = tx;
    }

    public BatchResult batch(long projectId, BatchRequest req) {
        validate(req);
        String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String action = "batch." + req.resource().toLowerCase(Locale.ROOT) + "." + req.action();
        String resourceType = req.resource().toLowerCase(Locale.ROOT);

        List<BatchItemResult> items = new ArrayList<>(req.ids().size());
        for (long id : req.ids()) {
            boolean ok = false;
            String code = null;
            String message = null;
            try {
                if (tx != null) {
                    // 异常必须冒泡，TransactionTemplate 才会回滚本条
                    tx.executeWithoutResult(status -> applyOne(projectId, id, req));
                } else {
                    applyOne(projectId, id, req);
                }
                ok = true;
            } catch (BusinessException e) {
                code = e.errorCode().name();
                message = e.getMessage();
            } catch (RuntimeException e) {
                code = ErrorCode.INTERNAL_ERROR.name();
                message = e.getClass().getSimpleName();
            }
            // 审计在事务外：失败条目同样留痕，同批次可由 batch= 反查
            audit.record(action, resourceType, String.valueOf(id),
                    "batch=" + tag + ", ok=" + ok + (code == null ? "" : ", code=" + code));
            items.add(new BatchItemResult(id, ok, code, message));
        }
        int succeeded = (int) items.stream().filter(BatchItemResult::ok).count();
        return new BatchResult(items.size(), succeeded, items.size() - succeeded, items);
    }

    private void validate(BatchRequest req) {
        if (req == null || req.ids() == null || req.ids().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "ids 不能为空");
        }
        if (req.ids().size() > BATCH_MAX) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "ids 超过 " + BATCH_MAX + " 条上限");
        }
        if (req.ids().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "ids 含非法 id");
        }
        if (req.resource() == null || !RESOURCES.contains(req.resource())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "未知资源: " + req.resource());
        }
        if (req.action() == null || !ACTIONS.contains(req.action())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "未知动作: " + req.action());
        }
        if (!"USER".equals(req.resource()) && !"delete".equals(req.action())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "资产只支持 delete 动作");
        }
        if ("setEnabled".equals(req.action()) && req.enabled() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "setEnabled 必须带 enabled");
        }
    }

    /** 单条业务动作：只调用既有服务，异常原样上抛交给外层事务与结果转换。 */
    private void applyOne(long projectId, long id, BatchRequest req) {
        switch (req.resource()) {
            case "DEVICE" -> devices.delete(projectId, id);
            case "LINK" -> links.delete(projectId, id);
            case "CREDENTIAL" -> credentials.delete(projectId, id);
            case "USER" -> {
                if ("delete".equals(req.action())) {
                    users.delete(id);
                } else {
                    UserSummary current = users.get(id);
                    users.update(id, current.displayName(), current.email(), current.orgId(),
                            req.enabled(), null);
                }
            }
            default -> throw new IllegalStateException("validate 已拦截未知资源: " + req.resource());
        }
    }
}
