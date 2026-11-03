# backend

Spring Boot 3 / Java 21 / Maven 单模块，包内分层：

```
src/main/java/com/qa/autotest/
├── api/            控制器、DTO、参数校验、异常映射
├── application/    用例编排、事务边界
├── domain/         实体、值对象、领域服务、仓储接口（无框架注解）
├── infrastructure/ 仓储实现、外部适配、执行器
└── common/         统一响应、错误码、工具
src/test/java/      ArchUnit 规则 + 单测 + 集成测试（@Tag("it")）
```

- 归属：**WS-02**（骨架 + ArchUnit）、**WS-04**（`db/migration`）、后续 B2~B7 各域。
- 分层依赖由 ArchUnit 守卫，违反即构建失败（基线报告 §12.2 R1~R5）。

```bash
mvn -B test          # 单测 + ArchUnit（@Tag("it") 默认跳过）
mvn -Pit test        # 集成测试，需 DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD
mvn spring-boot:run  # 本地起服务（:8081），GET /api/_ping
```

- 配置：`application.yml` + `dev/test/prod` 三个 profile；`DB_PASSWORD` **无默认值**，缺失即启动失败。
- 覆盖率：`mvn test` 后看 `target/site/jacoco/index.html`（只统计不设红线）。
