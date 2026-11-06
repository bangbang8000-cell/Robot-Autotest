package com.qa.autotest.application.execution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 日志频道：seq 递增、级别识别、订阅补尾部、flush 才推送。 */
class RunLogServiceTest {

    private final RunLogService logs = new RunLogService(ExecutionTestSupport.CLOCK);

    @Test
    void 发布后序号递增且级别可识别() {
        logs.publish("RUN-1", "pabot", "INFO running suite");
        logs.publish("RUN-1", "pabot", "ERROR opening library");
        logs.publish("RUN-1", "pabot", "WARN device busy");

        List<RunLogService.LogLine> recent = logs.recent("RUN-1", 10);
        assertEquals(3, recent.size());
        assertEquals(1, recent.get(0).seq());
        assertEquals(3, recent.get(2).seq());
        assertEquals("ERROR", recent.get(1).level());
        assertEquals("WARN", recent.get(2).level());
    }

    @Test
    void 订阅时立即补最近两百行且后续按批推送() {
        for (int i = 1; i <= 250; i++) {
            logs.publish("RUN-1", "pabot", "line-" + i);
        }
        List<String> received = new ArrayList<>();
        RunLogService.Registration registration =
                logs.subscribe("RUN-1", batch -> batch.forEach(line -> received.add(line.text())));

        assertEquals(200, received.size());
        assertEquals("line-51", received.get(0));

        logs.publish("RUN-1", "pabot", "after-subscribe");
        assertEquals(200, received.size());
        // pending 是 FIFO：补发后队列里还压着 250 行旧数据，每轮 flush 只取 50 行，
        // 新行要排到后面才轮到；生产里 100ms 一轮，这里循环到它出现为止。
        for (int i = 0; i < 12 && !received.contains("after-subscribe"); i++) {
            logs.flush();
        }
        assertTrue(received.size() > 200);
        assertTrue(received.contains("after-subscribe"), "订阅后新增的行必须推达");
        assertEquals(received.size(), new HashSet<>(received).size(),
                "同一行不得推两遍：补发与增量必须不重叠");
        assertFalse(received.contains("line-1"),
                "补发窗口(line-51..line-250)之外的行不得被 flush 补一遍");

        registration.close();
        logs.publish("RUN-1", "pabot", "after-close");
        logs.flush();
        assertTrue(!received.contains("after-close"));
    }

    @Test
    void 完成后残留日志仍会推送() {
        List<String> received = new ArrayList<>();
        logs.subscribe("RUN-1", batch -> batch.forEach(line -> received.add(line.text())));
        logs.publish("RUN-1", "system", "done");
        logs.complete("RUN-1");
        assertTrue(received.contains("done"));
        assertTrue(logs.completed("RUN-1"));
    }

    @Test
    void 不同_run_互不串流() {
        logs.publish("RUN-A", "pabot", "a-line");
        logs.publish("RUN-B", "pabot", "b-line");
        assertEquals(1, logs.recent("RUN-A", 10).size());
        assertEquals("a-line", logs.recent("RUN-A", 10).get(0).text());
        assertEquals("b-line", logs.recent("RUN-B", 10).get(0).text());
    }
}
