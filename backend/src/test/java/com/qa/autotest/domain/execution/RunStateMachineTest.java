package com.qa.autotest.domain.execution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 运行状态机单测（EXE-01）。 */
class RunStateMachineTest {

    @Test
    @DisplayName("全部合法边均可跃迁")
    void 合法边全通过() {
        for (RunState from : RunState.values()) {
            for (RunState to : RunState.values()) {
                if (!RunStateMachine.allowed(from, to)) {
                    continue;
                }
                RunStateMachine machine = new RunStateMachine(from);
                assertDoesNotThrow(() -> machine.transition(to), from + " -> " + to);
                assertEquals(to, machine.state());
            }
        }
    }

    @Test
    @DisplayName("非法跃迁抛 IllegalStateTransition")
    void 非法跃迁抛错() {
        RunStateMachine machine = new RunStateMachine(RunState.QUEUED);
        IllegalStateTransition ex1 = assertThrows(IllegalStateTransition.class,
                () -> machine.transition(RunState.RUNNING));
        assertEquals(RunState.QUEUED, ex1.from());
        assertEquals(RunState.RUNNING, ex1.to());

        RunStateMachine finished = new RunStateMachine(RunState.FINISHED);
        assertThrows(IllegalStateTransition.class, () -> finished.transition(RunState.RUNNING));

        RunStateMachine canceled = new RunStateMachine(RunState.CANCELED);
        assertThrows(IllegalStateTransition.class, () -> canceled.transition(RunState.FINISHED));
    }

    @Test
    @DisplayName("preparing -> running 缺租约或产物目录时拒绝")
    void 前置条件校验() {
        RunStateMachine machine = new RunStateMachine(RunState.PREPARING);
        assertThrows(IllegalStateTransition.class,
                () -> machine.transition(RunState.RUNNING, false, "租约已持有且产物目录已建"));
        assertEquals(RunState.PREPARING, machine.state());

        machine.transition(RunState.RUNNING, true, "租约已持有且产物目录已建");
        assertEquals(RunState.RUNNING, machine.state());
    }

    @Test
    @DisplayName("终态无出边")
    void 终态无出边() {
        for (RunState terminal : new RunState[]{RunState.FINISHED, RunState.FAILED, RunState.CANCELED}) {
            assertTrue(terminal.terminal());
            for (RunState to : RunState.values()) {
                assertTrue(!RunStateMachine.allowed(terminal, to), terminal + " 不应能到 " + to);
            }
        }
    }
}
