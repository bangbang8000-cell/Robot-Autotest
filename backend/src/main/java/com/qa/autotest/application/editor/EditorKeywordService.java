package com.qa.autotest.application.editor;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.EditorKeywordRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.editor.EditorKeyword;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 关键词元数据（EDT-04 / FR-v0.3-12 ~ 15）。
 *
 * <p>口径：{@code projectId = 0} 为平台级（BuiltIn / 已安装库），任何登录用户可读；
 * {@code projectId > 0} 为项目私有，读需 {@code CASE:READ}，增 / 改 / 删分别需
 * {@code CASE:CREATE} / {@code CASE:UPDATE} / {@code CASE:DELETE}。
 * 平台级词条的增删改只有全局管理员可做。
 */
@Service
public class EditorKeywordService {

    private static final int MAX_LIMIT = 500;
    private static final int MAX_ARGS = 50;
    private static final int MAX_ARG_LENGTH = 64;
    private static final int MAX_DOC_LENGTH = 4000;
    private static final Set<String> SOURCES = Set.of("builtin", "library", "project");

    /** 新增 / 修改入参。projectId 省略时按平台级处理。 */
    public record KeywordCommand(Long projectId, String library, String name,
                                 List<String> args, String doc, String source) {
    }

    private final EditorKeywordRepository keywords;
    private final AccessControlService access;
    private final AuditService audit;

    public EditorKeywordService(EditorKeywordRepository keywords, AccessControlService access,
                                AuditService audit) {
        this.keywords = keywords;
        this.access = access;
        this.audit = audit;
    }

    /** 搜索：项目私有 + 平台级词条。 */
    public List<EditorKeyword> search(Long projectId, String query, String library, String source,
                                      Integer limit) {
        long pid = projectOf(projectId);
        if (pid > 0) {
            access.bindProject(pid);
            access.require(PermissionResource.CASE, PermissionAction.READ);
        }
        return keywords.search(pid, query, library, source, clampLimit(limit));
    }

    public EditorKeyword create(KeywordCommand command) {
        long pid = projectOf(command.projectId());
        authorizeWrite(pid, PermissionAction.CREATE);
        EditorKeyword draft = new EditorKeyword(0, pid, requireLibrary(command.library()),
                requireName(command.name()), sanitizeArgs(command.args()),
                sanitizeDoc(command.doc()), sourceOf(command.source(), pid));
        long id = keywords.create(draft, TenantContext.userId());
        audit.record("editor.keyword.create", "editor_keyword", String.valueOf(id), draft.name());
        return new EditorKeyword(id, draft.projectId(), draft.library(), draft.name(), draft.args(),
                draft.doc(), draft.source());
    }

    public EditorKeyword update(long id, KeywordCommand command) {
        EditorKeyword existing = keywords.find(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "关键词不存在：" + id));
        long pid = projectOf(command.projectId());
        if (existing.projectId() != pid) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能跨作用域修改关键词");
        }
        authorizeWrite(pid, PermissionAction.UPDATE);
        EditorKeyword updated = new EditorKeyword(id, pid, requireLibrary(command.library()),
                requireName(command.name()), sanitizeArgs(command.args()),
                sanitizeDoc(command.doc()), sourceOf(command.source(), pid));
        keywords.update(updated, TenantContext.userId());
        audit.record("editor.keyword.update", "editor_keyword", String.valueOf(id), updated.name());
        return updated;
    }

    public void delete(long id, Long projectId) {
        EditorKeyword existing = keywords.find(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "关键词不存在：" + id));
        long pid = projectOf(projectId);
        if (existing.projectId() != pid) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能跨作用域删除关键词");
        }
        authorizeWrite(pid, PermissionAction.DELETE);
        keywords.delete(id, pid);
        audit.record("editor.keyword.delete", "editor_keyword", String.valueOf(id), existing.name());
    }

    private void authorizeWrite(long projectId, PermissionAction action) {
        if (projectId == 0) {
            if (!access.isAdmin()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "平台级关键词只有管理员可维护");
            }
            TenantContext.setProjectId(null);
            return;
        }
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, action);
    }

    private static long projectOf(Long projectId) {
        if (projectId == null) {
            return 0;
        }
        if (projectId < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 不能为负数");
        }
        return projectId;
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 200;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String requireLibrary(String library) {
        String value = library == null ? "" : library.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "library 不能为空");
        }
        if (value.length() > 128) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "library 超过 128 字符");
        }
        return value;
    }

    private static String requireName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "name 不能为空");
        }
        if (value.length() > 256) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "name 超过 256 字符");
        }
        return value;
    }

    /** 参数名清洗：去空、截断、限个数。包内可见便于单测。 */
    static List<String> sanitizeArgs(List<String> args) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }
        return args.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .limit(MAX_ARGS)
                .map(value -> value.length() > MAX_ARG_LENGTH
                        ? value.substring(0, MAX_ARG_LENGTH) : value)
                .toList();
    }

    static String sanitizeDoc(String doc) {
        if (doc == null || doc.isBlank()) {
            return null;
        }
        String value = doc.trim();
        return value.length() > MAX_DOC_LENGTH ? value.substring(0, MAX_DOC_LENGTH) : value;
    }

    static String sourceOf(String source, long projectId) {
        if (source == null || source.isBlank()) {
            return projectId > 0 ? "project" : "library";
        }
        String value = source.trim().toLowerCase(java.util.Locale.ROOT);
        if (!SOURCES.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "source 只能是 builtin / library / project");
        }
        return value;
    }
}
