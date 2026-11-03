package com.qa.autotest.domain.execution;

/**
 * 非法状态跃迁。执行域内不得用返回 false 代替抛出。
 */
public class IllegalStateTransition extends RuntimeException {

    private final RunState from;
    private final RunState to;

    public IllegalStateTransition(RunState from, RunState to) {
        super("非法运行状态跃迁: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public IllegalStateTransition(RunState from, RunState to, String detail) {
        super("非法运行状态跃迁: " + from + " -> " + to + " (" + detail + ")");
        this.from = from;
        this.to = to;
    }

    public RunState from() {
        return from;
    }

    public RunState to() {
        return to;
    }
}
