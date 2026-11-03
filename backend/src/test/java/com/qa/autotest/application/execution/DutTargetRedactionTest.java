package com.qa.autotest.application.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TC-v0.4-703：DUT 目标的文本形态必须可进日志与证据 —— 有主体、有跳板，没有口令、没有私钥路径。
 */
class DutTargetRedactionTest {

    private static final String SECRET = "dut-secret-probe-2026";

    @Test
    void 字符串形态不含口令且保留用户与主机() {
        DutTarget target = new DutTarget("10.202.54.106", 22, "root", SECRET, null, null, null);
        String text = target.toString();
        assertFalse(text.contains(SECRET), "toString 不得含口令");
        assertTrue(text.contains("root@10.202.54.106"), "toString 应含 user@host，实际 " + text);
    }

    @Test
    void 跳板模式追加via与跳板账号() {
        DutTarget target = new DutTarget("10.202.54.106", 22, "root", SECRET, null,
                "192.168.180.2", "evergreen");
        String text = target.toString();
        assertTrue(text.contains("via"), "跳板模式应含 via，实际 " + text);
        assertTrue(text.contains("evergreen@192.168.180.2"), "跳板应写成 jumpUser@jumpHost");
        assertFalse(text.contains(SECRET));
    }

    @Test
    void 非默认端口写进字符串形态() {
        DutTarget target = new DutTarget("10.202.54.106", 2222, "root", SECRET, null, null, null);
        String text = target.toString();
        assertTrue(text.contains("root@10.202.54.106:2222"), "端口非 22 时应带端口，实际 " + text);
        assertFalse(text.contains(SECRET));
    }

    @Test
    void 默认端口不冗余输出且公钥模式不泄露私钥路径() {
        DutTarget plain = new DutTarget("10.202.54.106", 22, "root", SECRET, null, null, null);
        assertFalse(plain.toString().contains(":22"), "默认端口不应出现在文本里");

        DutTarget withKey = new DutTarget("10.202.54.106", 22, "root", SECRET,
                "/home/root/.ssh/id_ed25519", "192.168.180.2", "evergreen");
        String text = withKey.toString();
        assertFalse(text.contains("/home/root/.ssh/id_ed25519"), "私钥完整路径不得出现在文本里");
        assertTrue(text.contains("key:yes"), "公钥模式只标记 key:yes，实际 " + text);
        assertFalse(text.contains(SECRET));
    }
}
