SHELL := /bin/bash
.DEFAULT_GOAL := help

.PHONY: help bootstrap build clean test lint ci-local \
        db-migrate db-undo db-reset db-audit secret-scan runner-install env-check

help:
	@echo "Robot-Autotest 构建入口（v0.1 地基）"
	@echo ""
	@echo "  开发"
	@echo "  make bootstrap       校验目录与工具链，创建运行时目录（幂等）"
	@echo "  make lint            密文扫描 + 前端 eslint + 后端编译"
	@echo "  make test            后端单测 + ArchUnit；有 DB_PASSWORD 时追加集成测试"
	@echo "  make build           构建后端 jar 与前端 dist"
	@echo "  make clean           清理构建产物"
	@echo ""
	@echo "  数据库（需 DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD）"
	@echo "  make db-migrate      flyway migrate（V1 建 38 表）"
	@echo "  make db-undo         按版本倒序执行 db/down/*.down.sql"
	@echo "  make db-reset        DROP + CREATE 测试库后重跑 migrate（只允许 *_it 库）"
	@echo "  make db-audit        38 表规范体检：审计四列 / 主键 / 时间类型 / 字符集"
	@echo ""
	@echo "  CI 与环境"
	@echo "  make ci-local        本地串行跑 lint → test → build（runner 未就绪时的降级路径）"
	@echo "  make secret-scan     扫描仓库内疑似硬编码口令"
	@echo "  make runner-install  在 149 安装自托管 runner（只装不注册，见脚本头注释）"
	@echo "  make env-check       校验环境基线（JDK / Maven / Node / 时区 / 变量）"

bootstrap:
	@bash scripts/bootstrap.sh

lint:
	@bash scripts/lint.sh

test:
	@bash scripts/test.sh

build:
	@bash scripts/build.sh

ci-local:
	@bash scripts/ci-local.sh

clean:
	@rm -rf artifacts/tmp backend/target frontend/dist packages/editor/dist
	@echo "[clean] 已清理构建产物"

db-migrate:
	@bash scripts/db-migrate.sh

db-undo:
	@bash scripts/db-undo.sh

db-reset:
	@bash scripts/db-reset.sh

db-audit:
	@bash scripts/db-audit.sh

secret-scan:
	@bash scripts/secret-scan.sh

runner-install:
	@bash scripts/runner-install.sh

env-check:
	@bash scripts/env-check.sh
