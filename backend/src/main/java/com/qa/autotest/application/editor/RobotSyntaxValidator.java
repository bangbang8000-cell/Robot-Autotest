package com.qa.autotest.application.editor;

/**
 * Robot 语法的服务端校验（EDT-09 / FR-v0.3-23）。
 *
 * <p>保存前必须能回答「这份内容能不能被 RF 解析」。实现依赖外部 RF 运行时，
 * 因此约定 {@code checked=false} 表示「环境没有 RF，本次没校验」——
 * 调用方不得把「没校验」当成「校验通过」，只在响应里如实标注。
 */
public interface RobotSyntaxValidator {

    SyntaxReport check(String content);

    /**
     * @param checked 是否真的跑了校验（false 表示环境缺少 RF，结论不可用）
     * @param ok      是否通过
     * @param line    出错行号（1 起；0 表示无法定位）
     * @param message 原始错误摘要
     */
    record SyntaxReport(boolean checked, boolean ok, int line, String message) {

        public static SyntaxReport unchecked() {
            return new SyntaxReport(false, true, 0, "");
        }

        public static SyntaxReport pass() {
            return new SyntaxReport(true, true, 0, "");
        }

        public static SyntaxReport fail(int line, String message) {
            return new SyntaxReport(true, false, line, message);
        }
    }
}
