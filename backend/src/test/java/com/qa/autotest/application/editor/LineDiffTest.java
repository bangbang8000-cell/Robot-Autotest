package com.qa.autotest.application.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.editor.LineDiff.DiffLine;
import com.qa.autotest.application.editor.LineDiff.Op;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 行级 diff 单测（EDT-09 / FR-v0.3-21）。 */
class LineDiffTest {

    @Test
    @DisplayName("内容相同：全部 EQUAL，行号两侧对齐")
    void 无变化() {
        List<DiffLine> lines = LineDiff.between("a\nb\n", "a\nb\n");

        assertEquals(2, lines.size());
        assertEquals(Op.EQUAL, lines.get(0).op());
        assertEquals(1, lines.get(0).leftNo());
        assertEquals(1, lines.get(0).rightNo());
        assertEquals(2, lines.get(1).leftNo());
    }

    @Test
    @DisplayName("纯新增行标 ADD，旧版行号为 0")
    void 纯新增() {
        List<DiffLine> lines = LineDiff.between("a\nc\n", "a\nb\nc\n");

        assertEquals(3, lines.size());
        assertEquals(Op.EQUAL, lines.get(0).op());
        assertEquals(Op.ADD, lines.get(1).op());
        assertEquals(0, lines.get(1).leftNo());
        assertEquals(2, lines.get(1).rightNo());
        assertEquals("b", lines.get(1).text());
    }

    @Test
    @DisplayName("纯删除行标 REMOVE，新版行号为 0")
    void 纯删除() {
        List<DiffLine> lines = LineDiff.between("a\nb\nc\n", "a\nc\n");

        assertEquals(Op.REMOVE, lines.get(1).op());
        assertEquals(2, lines.get(1).leftNo());
        assertEquals(0, lines.get(1).rightNo());
    }

    @Test
    @DisplayName("改一行 = 一删一增，其余保持 EQUAL")
    void 修改一行() {
        List<DiffLine> lines = LineDiff.between("a\nb\nc\n", "a\nB\nc\n");

        assertEquals(4, lines.size());
        assertEquals(Op.REMOVE, lines.get(1).op());
        assertEquals(Op.ADD, lines.get(2).op());
        assertEquals(Op.EQUAL, lines.get(3).op());
        assertEquals(3, lines.get(3).leftNo());
        assertEquals(3, lines.get(3).rightNo());
    }

    @Test
    @DisplayName("空文本：旧空→新有内容全为 ADD；新空→全为 REMOVE")
    void 空文本() {
        List<DiffLine> added = LineDiff.between("", "x\n");
        List<DiffLine> removed = LineDiff.between("x\n", "");

        assertTrue(added.stream().allMatch(line -> line.op() == Op.ADD));
        assertTrue(removed.stream().allMatch(line -> line.op() == Op.REMOVE));
    }

    @Test
    @DisplayName("换行符差异（CRLF vs LF）不算变更")
    void 换行符归一() {
        List<DiffLine> lines = LineDiff.between("a\r\nb\r\n", "a\nb\n");

        assertTrue(lines.stream().allMatch(line -> line.op() == Op.EQUAL));
    }
}
