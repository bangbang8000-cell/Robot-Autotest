package com.qa.autotest.application.system;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.iam.Audited;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 部署 / 回滚事件留痕（HRD-02：{@code system:deploy}）。
 *
 * <p>平台自身不做发布编排，版本切换由发布脚本完成；本服务提供唯一上报入口，
 * 让「谁在什么时候把哪个版本切上去/回滚」在审计里可查。
 */
@Service
public class DeployRecordService {

    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,31}");

    private static final int MAX_NOTE = 120;

    public enum Mode {
        DEPLOY, ROLLBACK
    }

    private final AccessControlService access;
    private final AuditService audit;

    public DeployRecordService(AccessControlService access, AuditService audit) {
        this.access = access;
        this.audit = audit;
    }

    /**
     * 记录一次部署或回滚。
     *
     * @param version 目标版本号（如 {@code v1.0.3}）
     * @param mode    {@code deploy} 或 {@code rollback}，大小写不敏感
     * @param note    可选备注，超长拒绝而不是截断
     */
    @Audited(action = "system:deploy", resource = "system")
    public DeployRecord record(String version, String mode, String note) {
        if (!access.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限：部署留痕仅管理员可写");
        }
        if (version == null || !VERSION.matcher(version.trim()).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "版本号不合法（限 1~32 位字母数字 . _ + -）：" + version);
        }
        Mode parsed;
        if (mode == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "mode 必填：deploy 或 rollback");
        }
        switch (mode.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "deploy" -> parsed = Mode.DEPLOY;
            case "rollback" -> parsed = Mode.ROLLBACK;
            default -> throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "mode 只能是 deploy 或 rollback：" + mode);
        }
        String trimmedNote = note == null ? "" : note.trim();
        if (trimmedNote.length() > MAX_NOTE) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "备注超长（上限 " + MAX_NOTE + " 字符）");
        }
        String target = version.trim();
        String after = "version=" + target + "; action=" + parsed.name()
                + (trimmedNote.isEmpty() ? "" : "; note=" + trimmedNote);
        audit.recordChange("system:deploy", "system", target, null, after);
        return new DeployRecord(target, parsed);
    }

    /** 上报结果：调用方（发布脚本）据此确认平台已留痕。 */
    public record DeployRecord(String version, Mode action) {
    }
}
