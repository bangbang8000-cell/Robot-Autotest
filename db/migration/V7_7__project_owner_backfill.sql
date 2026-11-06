-- V7_7__project_owner_backfill.sql · PRJ-01 案 A（2026-11-06 大师一次性授权「按推荐值」）
--
-- 归属 WBS #67 ｜ 对应 `docs/plan/P1-产品口径两案清单-20261106.md` §5
--
-- 为什么进 Flyway：`docs/evidence/v0.2/prj01/README.md` §六 的一次性 SQL 里写死了
-- `project.id=1 / owner_id=2`，且只在本机执行过。其它环境漏执行时 owner 保护是
-- **静默失效**（不报错、只是「所有者可被移除」），属于真实基线风险。
--
-- 与一次性 SQL 的区别：这里不写死任何 id，按「每个项目 admin 中 user_id 最小者」
-- 通用回填；只补 owner_id IS NULL 的行，重复执行零影响（幂等）。
UPDATE `project_member` SET `member_role` = 'admin' WHERE `member_role` = 'owner';

UPDATE `project` p
JOIN (
    SELECT `project_id`, MIN(`user_id`) AS owner_user_id
    FROM `project_member`
    WHERE `member_role` = 'admin'
    GROUP BY `project_id`
) m ON m.project_id = p.id
SET p.`owner_id` = m.owner_user_id
WHERE p.`owner_id` IS NULL;
