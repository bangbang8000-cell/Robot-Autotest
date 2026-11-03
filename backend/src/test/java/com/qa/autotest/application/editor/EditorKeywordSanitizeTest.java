package com.qa.autotest.application.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 关键词元数据入参清洗单测（EDT-04，FR-v0.3-14 / FR-v0.3-15）。 */
class EditorKeywordSanitizeTest {

    @Test
    @DisplayName("参数名去空、去空白项，空集合归一为不可变空列表")
    void 参数清洗() {
        assertEquals(List.of(), EditorKeywordService.sanitizeArgs(null));
        assertEquals(List.of(), EditorKeywordService.sanitizeArgs(List.of(" ", "")));
        assertEquals(List.of("actual", "expected"),
                EditorKeywordService.sanitizeArgs(java.util.Arrays.asList(" actual ", null, "expected")));
    }

    @Test
    @DisplayName("参数名超长截到 64，个数上限 50")
    void 参数截断() {
        String longName = "a".repeat(80);
        assertEquals(64, EditorKeywordService.sanitizeArgs(List.of(longName)).get(0).length());

        List<String> many = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            many.add("arg" + i);
        }
        assertEquals(50, EditorKeywordService.sanitizeArgs(many).size());
    }

    @Test
    @DisplayName("文档空白归一为 null，超长截到 4000")
    void 文档清洗() {
        assertNull(EditorKeywordService.sanitizeDoc("  "));
        assertEquals("短文档", EditorKeywordService.sanitizeDoc(" 短文档 "));
        assertEquals(4000, EditorKeywordService.sanitizeDoc("x".repeat(5000)).length());
    }

    @Test
    @DisplayName("source 缺省按作用域推断，非法值直接拒绝")
    void 来源推断() {
        assertEquals("library", EditorKeywordService.sourceOf(null, 0));
        assertEquals("project", EditorKeywordService.sourceOf(null, 7));
        assertEquals("builtin", EditorKeywordService.sourceOf(" BuiltIn ", 7));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> EditorKeywordService.sourceOf("vendor", 0));
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
    }
}
