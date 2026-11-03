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
 *
 * <h2>已知信息丢失（2026-11-04 确认，大师已接受）</h2>
 *
 * <p>真实实验室台账（如「详细测试流程」页）除编号/标题/分类/预置条件/预期结果外，还有
 * <b>测试步骤、A 端命令、B 端命令、交换机配置</b>等列。当前 {@link ImportRow} 没有承载
 * 这些内容的字段，导入时会被<b>静默忽略</b>（不报错、也不入库）。
 *
 * <p>这是刻意的取舍：先让「台账能导进来」（补表头别名），而不在导入器里硬塞一个
 * 挂错语义的字段。要真正支持带命令的台账，需要扩展用例模型 —— 那属于产品设计变更，
 * 须走评估 → PRD，不在补别名的范围内。记录见 {@code fixtures/README.md}。
 *
 * <h2>表头解析规则</h2>
 *
 * <p>① 表头行 = 前 10 行里第一处含任一已知别名的行；② 别名匹配忽略大小写与首尾空白；
 * ③ <b>多列撞同一字段时先出现的列赢</b>（{@code columns.put} 前用 {@code containsKey} 判重），
 * 后面的列静默丢弃 —— 例如真实台账同时有「一级分类」和「测试类型」，两者都映射到
 * {@code category}，最终取「一级分类」（"基本功能测试"），而「测试类型」（"功能"）被丢弃。
 * 这个丢弃是有意的：语义上「一级分类」粒度更合适。工具
 * {@code tools/check_import_header.py} 会对撞字段打 WARN，别当成没事。
 *
 * <p>④ 没有优先级列时全部落 {@link Priority#P2}（{@code Priority.from("")} 的默认），
 * 不报错。真实台账确实没有优先级列，所以 226 条导入后都是 P2 —— 需要真实分级得另补一列，
 * 但「执行顺序」那种自然语言（"第二优先：其余70项"）不能当地优先级喂进来，{@code Priority.from}
 * 会抛「非法优先级」把整行判错。
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
        alias("caseCode", "用例编号", "编号", "case_code", "casecode", "external_id",
                // 真实实验室台账用的是「测试编号」（TN-IMP-xxx 那一套）。原别名表只收
                // 「用例编号/编号」，导致这份表连必需列都认不出来、preview 直接抛
                // 「缺少必需列」。加别名是让它「能读」，不代表那几列命令也能存下来，
                // 见类注释「已知信息丢失」。
                "测试编号");
        alias("title", "标题", "用例名称", "名称", "title", "测试用例");
        alias("category", "分类", "类型", "case_kind", "category", "一级分类", "测试类型");
        alias("priority", "优先级", "priority");
        alias("precondition", "前置条件", "前置", "precondition", "预置条件");
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
