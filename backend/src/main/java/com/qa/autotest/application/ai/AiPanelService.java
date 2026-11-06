package com.qa.autotest.application.ai;

import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.ai.AiActionResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * AI 面板编排（AIX-04 / AIX-05）：建议 / 发起执行（二次确认）/ 效果评估，
 * 全部动作写审计；无 CLI 时降级而不阻断；一键终止不依赖 AI 是否可用。
 */
@Service
public class AiPanelService {

    private final AiCliProbe probe;
    private final ContextPacker packer;
    private final AiProcessManager processes;
    private final AuditService audit;
    private final AtomicLong sessions = new AtomicLong(1000);
    private final Map<Long, Boolean> openSessions = new ConcurrentHashMap<>();

    public AiPanelService(AiCliProbe probe, ContextPacker packer, AiProcessManager processes,
                          AuditService audit) {
        this.probe = probe;
        this.packer = packer;
        this.processes = processes;
        this.audit = audit;
    }

    public Map<String, Object> state(long projectId) {
        Map<String, Object> result = new LinkedHashMap<>(probe.probe());
        result.put("projectId", projectId);
        result.put("timeoutSeconds", processes.timeoutSeconds());
        result.put("encoding", processes.encoding());
        result.put("contextMaxChars", ContextPacker.DEFAULT_MAX_CHARS);
        result.put("executeRequiresConfirmation", true);
        return result;
    }

    public long open(long projectId, long userId) {
        long id = sessions.incrementAndGet();
        openSessions.put(id, true);
        audit.record("AI_SESSION_OPEN", "ai_session", String.valueOf(id),
                "project=" + projectId + "; user=" + userId);
        return id;
    }

    /** L1 建议：无论 CLI 是否可用都能给静态结论，永不抛「AI 不可用」。 */
    public AiActionResult suggest(long sessionId, Map<String, String> context) {
        String packed = packer.pack(context, ContextPacker.DEFAULT_MAX_CHARS);
        boolean degraded = !probe.available();
        String summary = degraded
                ? "CLI 不可用，仅提供脱敏上下文摘要（" + packed.length() + " 字符）"
                : "上下文已打包，可执行 L2 解释（" + packed.length() + " 字符）";
        AiActionResult result = new AiActionResult("SUGGEST", "L1", degraded, summary, true);
        audit.record("AI_SUGGEST", "ai_action", String.valueOf(sessionId), summary);
        return result;
    }

    /** L3 发起执行：二次确认是硬闸门，缺确认一律 403，不静默跳过。 */
    public AiActionResult execute(long sessionId, String target, boolean confirmed) {
        if (!Boolean.TRUE.equals(openSessions.get(sessionId))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在或已关闭");
        }
        if (!confirmed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "发起执行必须二次确认");
        }
        if (!probe.available()) {
            AiActionResult degraded = new AiActionResult("EXECUTE", "L3", true,
                    "CLI 不可用，动作降级为仅记录，不发起执行", true);
            audit.record("AI_EXECUTE_DEGRADED", "ai_action",
                    String.valueOf(sessionId), degraded.summary());
            return degraded;
        }
        String summary = "已按二次确认发起执行: " + (target == null ? "" : target);
        audit.record("AI_EXECUTE", "ai_action", String.valueOf(sessionId), summary);
        return new AiActionResult("EXECUTE", "L3", false, summary, true);
    }

    public AiActionResult evaluate(long sessionId, int improved, int regressed) {
        String summary = "效果评估 improved=" + improved + "; regressed=" + regressed;
        audit.record("AI_EVALUATE", "ai_action", String.valueOf(sessionId), summary);
        return new AiActionResult("EVALUATE", "L1", false, summary, true);
    }

    /** 一键终止：关闭会话、终止进程树、写审计；与 AI 是否可用无关。 */
    public AiActionResult terminate(long sessionId, ProcessHandle process) {
        openSessions.remove(sessionId);
        processes.terminate(process);
        String summary = "会话已关闭，进程树已终止";
        audit.record("AI_TERMINATE", "ai_action", String.valueOf(sessionId), summary);
        return new AiActionResult("TERMINATE", "L1", false, summary, true);
    }
}
