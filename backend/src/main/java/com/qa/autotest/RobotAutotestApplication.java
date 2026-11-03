package com.qa.autotest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 平台后端启动类（v0.1 骨架）。
 *
 * <p>本版只有分层骨架与 {@code /api/_ping}，不含任何业务功能；业务模块按
 * {@code api → application → domain} 方向逐层落地，依赖方向由 ArchUnit 守卫。
 */
@SpringBootApplication
public class RobotAutotestApplication {

    public static void main(String[] args) {
        SpringApplication.run(RobotAutotestApplication.class, args);
    }
}
