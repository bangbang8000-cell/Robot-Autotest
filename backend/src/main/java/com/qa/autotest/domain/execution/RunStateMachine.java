package com.qa.autotest.domain.execution;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 运行状态机。单个 run 一个实例，不跨线程共享。
 *
 * <p>合法边与 docs/release/v0.4-execution/DEV-PLAN.md §2.1 一致：
 * queued -> preparing/canceled；preparing -> running/failed/canceled；
 * running -> finished/failed/canceled；终态无出边。</p>
 */
public final class RunStateMachine {

    private static final Map<RunState, Set<RunState>> ALLOWED = buildAllowed();

    private RunState state;

    public RunStateMachine() {
        this(RunState.QUEUED);
    }

    public RunStateMachine(RunState initial) {
        if (initial == null) {
            throw new IllegalArgumentException("初始状态不能为空");
        }
        this.state = initial;
    }

    public RunState state() {
        return state;
    }

    public void transition(RunState to) {
        if (!allowed(state, to)) {
            throw new IllegalStateTransition(state, to);
        }
        state = to;
    }

    /**
     * 带前置条件的跃迁。典型用法：preparing -> running 必须「租约已持有且产物目录已建」。
     */
    public void transition(RunState to, boolean precondition, String preconditionName) {
        if (!allowed(state, to)) {
            throw new IllegalStateTransition(state, to);
        }
        if (!precondition) {
            throw new IllegalStateTransition(state, to, "前置条件未满足: " + preconditionName);
        }
        state = to;
    }

    public static boolean allowed(RunState from, RunState to) {
        if (from == null || to == null) {
            return false;
        }
        Set<RunState> targets = ALLOWED.get(from);
        return targets != null && targets.contains(to);
    }

    private static Map<RunState, Set<RunState>> buildAllowed() {
        Map<RunState, Set<RunState>> map = new EnumMap<>(RunState.class);
        map.put(RunState.QUEUED, EnumSet.of(RunState.PREPARING, RunState.CANCELED));
        map.put(RunState.PREPARING, EnumSet.of(RunState.RUNNING, RunState.FAILED, RunState.CANCELED));
        map.put(RunState.RUNNING, EnumSet.of(RunState.FINISHED, RunState.FAILED, RunState.CANCELED));
        map.put(RunState.FINISHED, EnumSet.noneOf(RunState.class));
        map.put(RunState.FAILED, EnumSet.noneOf(RunState.class));
        map.put(RunState.CANCELED, EnumSet.noneOf(RunState.class));
        return map;
    }
}
