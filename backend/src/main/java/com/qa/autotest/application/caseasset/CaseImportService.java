package com.qa.autotest.application.caseasset;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.TestCaseRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.caseasset.Priority;
import com.qa.autotest.domain.caseasset.ReviewStatus;
import com.qa.autotest.domain.caseasset.TestCase;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 461 条台账 Excel 导入（CAS-03）。
 *
 * <p>两步式：{@link #preview} 只解析与校验、不落库，把错误整表返回；{@link #commit}
 * 在一个事务里写库，任何一行冲突就整批回滚。
 */
@Service
public class CaseImportService {

    /** 预览行。 */
    public record ImportRow(int rowNum, String caseCode, String title, String category,
                            String priority, String precondition, String expected, String domainTag,
                            String capability, String description) {
    }

    /** 一行校验错误。 */
    public record RowError(int rowNum, String field, String message) {
    }

    /** 预览结果。 */
    public record ImportPreview(int totalRows, int validRows, List<RowError> errors,
                                List<ImportRow> rows) {
    }

    /** 提交结果。 */
    public record ImportResult(int imported, int skipped) {
    }

    /** 表头别名（中英文都收，避免各版本 Excel 反复改字段名）。 */
    private static final Map<String, String> HEADER_ALIASES = new LinkedHashMap<>();

    static {
        alias("caseCode", "用例编号", "编号", "case_code", "casecode", "external_id");
        alias("title", "标题", "用例名称", "名称", "title");
        alias("category", "分类", "类型", "case_kind", "category");
        alias("priority", "优先级", "priority");
        alias("precondition", "前置条件", "前置", "precondition");
        alias("expected", "预期结果", "预期", "expected");
        alias("domainTag", "技术域", "域", "domain", "domain_tag");
        alias("capability", "能力", "能力项", "capability");
        alias("description", "描述", "说明", "description");
    }

    private static void alias(String field, String... names) {
        for (String name : names) {
            HEADER_ALIASES.put(name.toLowerCase(Locale.ROOT), field);
        }
    }

    private final TestCaseRepository cases;
    private final AccessControlService access;
    private final AuditService audit;

    public CaseImportService(TestCaseRepository cases, AccessControlService access,
                             AuditService audit) {
        this.cases = cases;
        this.access = access;
        this.audit = audit;
    }

    /** 解析 + 校验，不写库。 */
    public ImportPreview preview(long projectId, InputStream input) {
        access.bindProject(projectId);
        access.require(PermissionResource.IMPORT, PermissionAction.EXECUTE);
        List<ImportRow> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Excel 没有工作表");
            }
            int headerRowIndex = findHeaderRow(sheet);
            Map<String, Integer> columns = readHeader(sheet.getRow(headerRowIndex));
            if (!columns.containsKey("caseCode") || !columns.containsKey("title")) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "缺少必需列：用例编号 / 标题");
            }
            DataFormatter formatter = new DataFormatter();
            java.util.Set<String> seenCodes = new java.util.HashSet<>();
            int total = 0;
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String code = cell(formatter, row, columns.get("caseCode"));
                String title = cell(formatter, row, columns.get("title"));
                String rawPriority = cell(formatter, row, columns.get("priority"));
                if (code.isEmpty() && title.isEmpty() && rawPriority.isEmpty()) {
                    continue;
                }
                total++;
                int rowNum = i + 1;
                if (code.isEmpty()) {
                    errors.add(new RowError(rowNum, "caseCode", "用例编号为空"));
                    continue;
                }
                if (!seenCodes.add(code.toUpperCase(Locale.ROOT))) {
                    errors.add(new RowError(rowNum, "caseCode", "文件内编号重复：" + code));
                    continue;
                }
                if (cases.findByCode(projectId, code).isPresent()) {
                    errors.add(new RowError(rowNum, "caseCode", "编号已存在于项目：" + code));
                    continue;
                }
                if (title.isEmpty()) {
                    errors.add(new RowError(rowNum, "title", "标题为空"));
                    continue;
                }
                String priority;
                try {
                    priority = Priority.from(rawPriority).name();
                } catch (IllegalArgumentException ex) {
                    errors.add(new RowError(rowNum, "priority", ex.getMessage()));
                    continue;
                }
                rows.add(new ImportRow(rowNum, code, title,
                        cell(formatter, row, columns.get("category")), priority,
                        cell(formatter, row, columns.get("precondition")),
                        cell(formatter, row, columns.get("expected")),
                        cell(formatter, row, columns.get("domainTag")),
                        cell(formatter, row, columns.get("capability")),
                        cell(formatter, row, columns.get("description"))));
            }
            int valid = rows.size();
            return new ImportPreview(total, valid, errors, rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Excel 解析失败：" + ex.getMessage());
        }
    }

    /** 事务提交；任一行冲突整批回滚。 */
    @Transactional
    public ImportResult commit(long projectId, List<ImportRow> rows) {
        access.bindProject(projectId);
        access.require(PermissionResource.IMPORT, PermissionAction.EXECUTE);
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "没有可导入的数据");
        }
        int imported = 0;
        for (ImportRow row : rows) {
            if (cases.codeExists(projectId, row.caseCode(), null)) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "第 " + row.rowNum() + " 行编号已存在：" + row.caseCode());
            }
            TestCase entity = new TestCase(null, projectId, row.caseCode().trim(), row.title(),
                    row.category(), Priority.from(row.priority()).name(), row.precondition(),
                    row.expected(), row.domainTag(), ReviewStatus.DRAFT.code(), "excel",
                    row.capability(), row.description());
            cases.insert(entity, TenantContext.userId());
            imported++;
        }
        audit.record("case.import", "test_case", String.valueOf(projectId), "imported=" + imported);
        return new ImportResult(imported, 0);
    }

    /** 找到表头行：第一处包含「用例编号」或 case_code 的行。 */
    private static int findHeaderRow(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 10); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String value = formatter.formatCellValue(row.getCell(c));
                if (HEADER_ALIASES.containsKey(value.trim().toLowerCase(Locale.ROOT))) {
                    return i;
                }
            }
        }
        throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "未找到表头行（前 10 行内）");
    }

    private static Map<String, Integer> readHeader(Row header) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        DataFormatter formatter = new DataFormatter();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String raw = formatter.formatCellValue(header.getCell(c)).trim();
            String field = HEADER_ALIASES.get(raw.toLowerCase(Locale.ROOT));
            if (field != null && !columns.containsKey(field)) {
                columns.put(field, c);
            }
        }
        return columns;
    }

    private static String cell(DataFormatter formatter, Row row, Integer column) {
        if (column == null || column < 0 || column >= row.getLastCellNum()) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(column)).trim();
    }
}
