package com.qa.autotest.domain.editor;

import java.util.List;

/**
 * 关键词元数据（EDT-04 / FR-v0.3-15）。
 *
 * @param projectId 0 = 平台级（BuiltIn / 已安装库）；&gt; 0 = 项目私有
 * @param library   来源库或分类名，面板按此分组
 * @param name      关键词名
 * @param args      libdoc 参数名（含默认值写法，如 {@code msg=None}），面板据此补占位符
 * @param doc       纯文本文档
 * @param source    builtin / library / project
 */
public record EditorKeyword(
        long id,
        long projectId,
        String library,
        String name,
        List<String> args,
        String doc,
        String source) {

    public EditorKeyword {
        args = args == null ? List.of() : List.copyOf(args);
    }
}
