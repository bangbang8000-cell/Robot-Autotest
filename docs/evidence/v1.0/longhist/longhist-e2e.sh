#!/usr/bin/env bash
# 长历史压测收口：RPT-03 / RPT-04 / PLN-02 三条「未压测」残余
#
# 原残余（WBS 第 79/80/85 行第 7 列）：
#   RPT-03 —— 「长历史未压测」：compare / baseline-candidates 只在个位数运行上验过；
#   RPT-04 —— 「十万级 test_result 下趋势取点与通过率聚合耗时未知」；
#   PLN-02 —— 「分页与时间范围未在真实长历史数据下压测（大 page 的 OFFSET 退化未验）」。
#
# 做法：一次性项目里造两档真实体量，用真 HTTP 重复取样，同时验「读数对不对」与「耗时多少」：
#   档位 A：2000 次运行 × 50 用例 = 100,000 行 test_result；100,000 行 PLAN_DOWNSTREAM_SKIPPED 审计
#   档位 B：再叠加 4000 次运行 = 6000 次运行 / 300,000 行 test_result（只看趋势与通过率的增长曲线）
# 末尾删净运行、结果、审计、项目与令牌，并断言计数归零。
#
# 用法：bash longhist-e2e.sh 2>&1 | tee logs/00-run.log
set -uo pipefail

BASE="http://127.0.0.1:8081"
EVID="$(cd "$(dirname "$0")" && pwd)"
WORK=/tmp/longhist-work
LOGS="$EVID/logs"
TS=$(date +%Y%m%d%H%M%S)
CODE="EVD-LONGHIST-$TS"
PASS=0
FAIL=0

rm -rf "$WORK"
mkdir -p "$WORK" "$LOGS"

pass() { printf '  PASS  %s\n' "$*"; }
fail() { printf '  FAIL  %s\n' "$*"; }
chk() { # 名称 实际 期望
  if [ "$2" = "$3" ]; then PASS=$((PASS + 1)); pass "$1 ($2)";
  else FAIL=$((FAIL + 1)); fail "$1：期望 $3，实际 $2"; fi
}

set -a
# shellcheck disable=SC1090
. /home/evergreen/.config/robot-autotest/env
set +a
C=mysql
command -v mariadb >/dev/null 2>&1 && C=mariadb
mysql_do() {
  "$C" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
    --default-character-set=utf8mb4 "$DB_NAME" "$@"
}
q() { mysql_do --batch --skip-column-names -e "$1" 2>/dev/null; }

echo "== 0. 环境与令牌 =="
mysql_do -e "select 1" >/dev/null 2>&1
chk "MySQL 可达" "$?" "0"
chk "后端可达（不带令牌应 401）" \
  "$(curl -s --noproxy '*' -o /dev/null -w '%{http_code}' "$BASE/api/projects")" "401"

read -r TOKEN HASH <<< "$(python3 - <<'PY'
import hashlib, secrets
t = secrets.token_urlsafe(32)
print(t, hashlib.sha256(t.encode()).hexdigest())
PY
)"
q "insert into api_token (user_id, name, token_hash, token_kind, expires_at)
   values (1, 'longhist-e2e', '$HASH', 'session', now(3) + interval 2 hour)"
chk "临时令牌已建" "$(q "select count(*) from api_token where token_hash='$HASH'")" "1"

get() { # 名称 路径（不含 /api）
  local name=$1 path=$2
  curl -s --noproxy '*' -o "$WORK/$name.body" -w '%{http_code}' \
    -H "Authorization: Bearer $TOKEN" "$BASE/api$path"
}

# 重复取样：5 次，输出 "状态码最小 中位 最大"（毫秒）
timeit() { # 名称 路径 次数
  local name=$1 path=$2 n=${3:-5} i code t
  : > "$WORK/$name.times"
  for i in $(seq 1 "$n"); do
    read -r code t <<< "$(curl -s --noproxy '*' -o "$WORK/$name.r$i.body" -w '%{http_code} %{time_total}' \
      -H "Authorization: Bearer $TOKEN" "$BASE/api$path")"
    printf '%s %s\n' "$code" "$t" >> "$WORK/$name.times"
  done
  python3 - "$WORK/$name.times" <<'PY'
import sys
rows = [l.split() for l in open(sys.argv[1]) if l.strip()]
codes = {c for c, _ in rows}
ms = sorted(float(t) * 1000 for _, t in rows)
med = ms[len(ms) // 2]
print('%s %.0f %.0f %.0f' % (','.join(sorted(codes)), ms[0], med, ms[-1]))
PY
}

# 断言耗时分位：名称 取样行("code min med max") 上限ms
chk_ms() {
  local name=$1 line=$2 budget=$3 code min med max
  read -r code min med max <<< "$line"
  chk "$name 状态码" "$code" "200"
  if [ "$max" -le "$budget" ]; then PASS=$((PASS + 1)); pass "$name 耗时 max ${max}ms ≤ ${budget}ms（min ${min} / 中位 ${med}）";
  else FAIL=$((FAIL + 1)); fail "$name 耗时 max ${max}ms 超出预算 ${budget}ms（min ${min} / 中位 ${med}）"; fi
}

echo
echo "== 1. 造数：2000 次运行 / 100,000 行 test_result / 100,000 行审计 =="
mysql_do 2>/dev/null <<SQL
delete from test_result where run_id in (select id from run where project_id in
  (select id from project where code like 'EVD-LONGHIST-%'));
delete from run where project_id in (select id from project where code like 'EVD-LONGHIST-%');
delete from audit_log where project_id in (select id from project where code like 'EVD-LONGHIST-%');
delete from project where code like 'EVD-LONGHIST-%';

insert into project (code, name, description, status, created_by)
values ('$CODE', '长历史压测', '一次性验证库，脚本末尾清理', 1, 1);
set @pid := (select id from project where code = '$CODE');

set session cte_max_recursion_depth = 200000;
insert into run (run_id, project_id, trigger_type, run_status, started_at, finished_at,
                 dut, link, pass_count, fail_count, skip_count, created_by, created_at)
with recursive s(n) as (select 1 union all select n + 1 from s where n < 2000)
select concat('$CODE-R', lpad(n, 4, '0')), @pid, 'manual', 'finished',
       date_add('2024-01-01 00:00:00', interval n * 30 minute),
       date_add('2024-01-01 00:05:00', interval n * 30 minute),
       'DUT-A', 'L0', 45, 5, 0, 1,
       date_add('2024-01-01 00:00:00', interval n * 30 minute)
  from s;

-- 每次运行 50 条用例：case 编号 01~50；每 10 个全局序号一个 FAIL（每次运行恰好 5 FAIL / 45 PASS）
insert into test_result (run_id, case_key, verdict, duration_ms)
with recursive s(n) as (select 1 union all select n + 1 from s where n < 100000)
select r.id,
       concat('Suite.Case', lpad(((s.n - 1) % 50) + 1, 2, '0')),
       if((s.n % 10) = 0, 'FAIL', 'PASS'),
       100 + (s.n % 900)
  from s
  join run r on r.run_id = concat('$CODE-R', lpad(floor((s.n - 1) / 50) + 1, 4, '0'));

-- 依赖跳过审计：10 万行，每行 detail 带唯一 downstream 序号（用来验证深翻页没串行）
insert into audit_log (project_id, actor_id, operator, action, resource_type, resource_id,
                       detail, occurred_at, created_at)
with recursive s(n) as (select 1 union all select n + 1 from s where n < 100000)
select @pid, 1, 'admin', 'PLAN_DOWNSTREAM_SKIPPED', 'plan', concat('p', s.n),
       concat('mark=skipped_dependency, downstream=', s.n),
       date_add('2025-01-01 00:00:00', interval s.n * 5 minute),
       date_add('2025-01-01 00:00:00', interval s.n * 5 minute)
  from s;
SQL
PID_=$(q "select id from project where code='$CODE'")
chk "专用项目已建" "$([ -n "$PID_" ] && echo ok)" "ok"
chk "运行 2000 次" "$(q "select count(*) from run where project_id=$PID_")" "2000"
chk "用例结果 100000 行" "$(q "select count(*) from test_result t join run r on r.id=t.run_id where r.project_id=$PID_")" "100000"
chk "跳过事件 100000 行" "$(q "select count(*) from audit_log where project_id=$PID_ and action='PLAN_DOWNSTREAM_SKIPPED'")" "100000"

CUR="$CODE-R2000"

echo
echo "== 2. 基准开销（无业务的列表接口，用来把固定成本从查询成本里剥出来） =="
chk_ms "项目列表 page=1（固定成本参照）" "$(timeit baseline_cost "/projects?page=1&size=20" 5)" 300

echo
echo "== 3. 档位 A：趋势取点（RPT-04） =="
chk "trend limit 50 GET 200" "$(get tr_a "/reports/$CUR/trend?projectId=$PID_&caseKey=Suite.Case01&limit=50")" "200"
chk_ms "trend(limit=50) 100k 行" "$(timeit tr_a_time "/reports/$CUR/trend?projectId=$PID_&caseKey=Suite.Case01&limit=50" 5)" 1000
python3 - "$WORK/tr_a.body" <<'PY' > "$WORK/tr_a.txt"
import json, sys
d = json.load(open(sys.argv[1], encoding='utf-8'))['data']
pts = d['points']
print('points=%d first=%s last=%s baselineMs=%s deviated=%s'
      % (len(pts), pts[0]['runId'].rsplit('-', 1)[-1], pts[-1]['runId'].rsplit('-', 1)[-1],
         d['baselineMs'], str(d['deviated']).lower()))
PY
tr_a=$(cat "$WORK/tr_a.txt")
echo "  读数：$tr_a"
chk "趋势点数 = limit 50（2000 点里取最近 50）" "$(printf '%s' "$tr_a" | sed 's/.*points=\([0-9]*\).*/\1/')" "50"
chk "返回升序，末点是最新一轮 R2000" "$(printf '%s' "$tr_a" | sed 's/.*last=\([^ ]*\).*/\1/')" "R2000"
chk "最早点是被 limit 裁掉的边界 R1951" "$(printf '%s' "$tr_a" | sed 's/.*first=\([^ ]*\).*/\1/')" "R1951"
chk "基线与偏离标记可用" "$(printf '%s' "$tr_a" | sed 's/.*deviated=\([a-z]*\).*/\1/')" "false"

echo
echo "== 4. 档位 A：通过率聚合（RPT-04） =="
chk "pass-rate(day) GET 200" "$(get pr_a "/reports/trend/pass-rate?projectId=$PID_&granularity=day&limit=52")" "200"
chk_ms "pass-rate(day) 2000 次运行" "$(timeit pr_a_time "/reports/trend/pass-rate?projectId=$PID_&granularity=day&limit=52" 5)" 1000
python3 - "$WORK/pr_a.body" <<'PY' > "$WORK/pr_a.txt"
import json, sys
b = json.load(open(sys.argv[1], encoding='utf-8'))['data']['buckets']
print('buckets=%d pass=%d fail=%d' % (len(b), sum(x['pass'] for x in b), sum(x['fail'] for x in b)))
PY
pr_a=$(cat "$WORK/pr_a.txt")
echo "  读数：$pr_a"
chk "41.7 天历史 → 42 个日桶" "$(printf '%s' "$pr_a" | sed 's/.*buckets=\([0-9]*\).*/\1/')" "42"
chk "日桶 PASS 合计 90000" "$(printf '%s' "$pr_a" | sed 's/.*pass=\([0-9]*\).*/\1/')" "90000"
chk "日桶 FAIL 合计 10000" "$(printf '%s' "$pr_a" | sed 's/.*fail=\([0-9]*\).*/\1/')" "10000"

echo
echo "== 5. 档位 A：对比与基线候选（RPT-03） =="
chk "compare GET 200" "$(get cmp_a "/reports/$CUR/compare?projectId=$PID_")" "200"
chk_ms "compare 2000 次历史" "$(timeit cmp_a_time "/reports/$CUR/compare?projectId=$PID_" 5)" 1000
python3 - "$WORK/cmp_a.body" <<'PY' > "$WORK/cmp_a.txt"
import json, sys
d = json.load(open(sys.argv[1], encoding='utf-8'))['data']
s = d['summary']
print('baseline=%s rows=%d newFail=%d newPass=%d bothFail=%d notRun=%d'
      % (d['baseline']['runId'].rsplit('-', 1)[-1], len(d['rows']),
         s['newFail'], s['newPass'], s['bothFail'], s['notRun']))
PY
cmp_a=$(cat "$WORK/cmp_a.txt")
echo "  读数：$cmp_a"
chk "自动基线取前一轮 R1999" "$(printf '%s' "$cmp_a" | sed 's/.*baseline=\([^ ]*\).*/\1/')" "R1999"
chk "对比行数 50（全量用例）" "$(printf '%s' "$cmp_a" | sed 's/.*rows=\([0-9]*\).*/\1/')" "50"
chk "两轮同结论：无新增失败" "$(printf '%s' "$cmp_a" | sed 's/.*newFail=\([0-9]*\).*/\1/')" "0"
chk "两轮同结论：无新增通过" "$(printf '%s' "$cmp_a" | sed 's/.*newPass=\([0-9]*\).*/\1/')" "0"
chk "两侧都失败 5 条" "$(printf '%s' "$cmp_a" | sed 's/.*bothFail=\([0-9]*\).*/\1/')" "5"
chk "未运行 0 条" "$(printf '%s' "$cmp_a" | sed 's/.*notRun=\([0-9]*\).*/\1/')" "0"

chk "baseline-candidates GET 200" "$(get cand_a "/reports/$CUR/baseline-candidates?projectId=$PID_")" "200"
chk_ms "baseline-candidates 2000 次历史" "$(timeit cand_a_time "/reports/$CUR/baseline-candidates?projectId=$PID_" 5)" 500
chk "候选上限 20（1999 条历史里截断）" "$(python3 -c "import json;print(len(json.load(open('$WORK/cand_a.body'))['data']))")" "20"
chk "候选最新一条是 R1999" "$(python3 -c "import json;print(json.load(open('$WORK/cand_a.body'))['data'][0]['runId'].rsplit('-',1)[-1])")" "R1999"

echo
echo "== 6. 档位 A：跳过事件深翻页与时间范围（PLN-02） =="
chk "audit page=1 GET 200" "$(get aud_1 "/audit?projectId=$PID_&action=PLAN_DOWNSTREAM_SKIPPED&page=1&size=20")" "200"
chk_ms "audit page=1（20 / 100000）" "$(timeit aud_1_time "/audit?projectId=$PID_&action=PLAN_DOWNSTREAM_SKIPPED&page=1&size=20" 5)" 1000
chk "audit page=5000 GET 200" "$(get aud_5000 "/audit?projectId=$PID_&action=PLAN_DOWNSTREAM_SKIPPED&page=5000&size=20")" "200"
chk_ms "audit page=5000（OFFSET 99980）" "$(timeit aud_5000_time "/audit?projectId=$PID_&action=PLAN_DOWNSTREAM_SKIPPED&page=5000&size=20" 5)" 1500
chk "audit 越界 page=5001 GET 200" "$(get aud_over "/audit?projectId=$PID_&action=PLAN_DOWNSTREAM_SKIPPED&page=5001&size=20")" "200"

python3 - "$WORK/aud_1_time.times" "$WORK/aud_5000_time.times" <<'PY' > "$WORK/aud_ratio.txt"
import sys


def med(p):
    v = sorted(float(l.split()[1]) for l in open(p) if l.strip())
    return v[len(v) // 2] * 1000


a, b = med(sys.argv[1]), med(sys.argv[2])
print('ratio=%.2f first=%.0fms deep=%.0fms' % (b / a, a, b))
PY
aud_ratio=$(cat "$WORK/aud_ratio.txt")
echo "  深翻页相对首页：$aud_ratio"
ratio=$(printf '%s' "$aud_ratio" | sed 's/ratio=\([0-9.]*\).*/\1/')
if python3 -c "import sys; sys.exit(0 if $ratio < 5 else 1)"; then
  PASS=$((PASS + 1)); pass "深翻页退化比 ${ratio}× 在 5× 预算内（OFFSET 99980）"
else
  FAIL=$((FAIL + 1)); fail "深翻页退化比 ${ratio}× 超过 5× 预算（OFFSET 99980）"
fi

python3 - "$WORK/aud_1.body" "$WORK/aud_5000.body" "$WORK/aud_over.body" <<'PY' > "$WORK/aud.txt"
import json, re, sys


def load(p):
    return json.load(open(p, encoding='utf-8'))['data']


def n(entry):
    m = re.search(r'downstream=(\d+)', entry['detail'])
    return int(m.group(1))


p1, p5000, over = load(sys.argv[1]), load(sys.argv[2]), load(sys.argv[3])
print('total=%d p1=%d p1_first=%d p5000=%d p5000_last=%d over_rows=%d over_total=%d'
      % (p1['total'], len(p1['items']), n(p1['items'][0]),
         len(p5000['items']), n(p5000['items'][-1]),
         len(over['items']), over['total']))
PY
aud=$(cat "$WORK/aud.txt")
echo "  读数：$aud"
chk "总数 100000" "$(printf '%s' "$aud" | sed 's/.*total=\([0-9]*\) .*/\1/')" "100000"
chk "首页 20 条且首条是第 100000 号（倒序）" "$(printf '%s' "$aud" | sed 's/.*p1_first=\([0-9]*\).*/\1/')" "100000"
chk "末页 20 条且最后一条是第 1 号（OFFSET 未错位）" "$(printf '%s' "$aud" | sed 's/.*p5000_last=\([0-9]*\).*/\1/')" "1"
chk "越界页返回 0 条但 total 不丢" "$(printf '%s' "$aud" | sed 's/.*over_rows=\([0-9]*\) over_total=\([0-9]*\).*/\1-\2/')" "0-100000"

chk "audit from/to GET 200" \
  "$(get aud_range "/audit?projectId=$PID_&action=PLAN_DOWNSTREAM_SKIPPED&from=2025-06-01&to=2025-06-30&page=1&size=20")" "200"
chk_ms "audit 时间范围（30 天窗口）" \
  "$(timeit aud_range_time "/audit?projectId=$PID_&action=PLAN_DOWNSTREAM_SKIPPED&from=2025-06-01&to=2025-06-30&page=1&size=20" 5)" 1000
expect_range=$(q "select count(*) from audit_log where project_id=$PID_ and action='PLAN_DOWNSTREAM_SKIPPED'
  and occurred_at >= '2025-06-01 00:00:00' and occurred_at < '2025-07-01 00:00:00'")
chk "时间范围 total 与库侧一致（$expect_range）" \
  "$(python3 -c "import json;print(json.load(open('$WORK/aud_range.body'))['data']['total'])")" "$expect_range"

echo
echo "== 7. 档位 B：再叠 4000 次运行 → 300,000 行，看增长曲线 =="
mysql_do 2>/dev/null <<SQL
set session cte_max_recursion_depth = 400000;
insert into run (run_id, project_id, trigger_type, run_status, started_at, finished_at,
                 dut, link, pass_count, fail_count, skip_count, created_by, created_at)
with recursive s(n) as (select 2001 union all select n + 1 from s where n < 6000)
select concat('$CODE-R', lpad(n, 4, '0')), $PID_, 'manual', 'finished',
       date_add('2024-01-01 00:00:00', interval n * 30 minute),
       date_add('2024-01-01 00:05:00', interval n * 30 minute),
       'DUT-A', 'L0', 45, 5, 0, 1,
       date_add('2024-01-01 00:00:00', interval n * 30 minute)
  from s;

insert into test_result (run_id, case_key, verdict, duration_ms)
with recursive s(n) as (select 100001 union all select n + 1 from s where n < 300000)
select r.id,
       concat('Suite.Case', lpad(((s.n - 1) % 50) + 1, 2, '0')),
       if((s.n % 10) = 0, 'FAIL', 'PASS'),
       100 + (s.n % 900)
  from s
  join run r on r.run_id = concat('$CODE-R', lpad(floor((s.n - 1) / 50) + 1, 4, '0'));
SQL
chk "档位 B 运行 6000 次" "$(q "select count(*) from run where project_id=$PID_")" "6000"
chk "档位 B 结果 300000 行" "$(q "select count(*) from test_result t join run r on r.id=t.run_id where r.project_id=$PID_")" "300000"

CUR_B="$CODE-R6000"
chk_ms "trend(limit=50) 300k 行" \
  "$(timeit tr_b_time "/reports/$CUR_B/trend?projectId=$PID_&caseKey=Suite.Case01&limit=50" 5)" 1000
chk_ms "pass-rate(day) 6000 次运行" \
  "$(timeit pr_b_time "/reports/trend/pass-rate?projectId=$PID_&granularity=day&limit=52" 5)" 1000
chk "档位 B 通过率 GET 200" \
  "$(get pr_b "/reports/trend/pass-rate?projectId=$PID_&granularity=day&limit=52")" "200"
chk "125 天历史被 limit 截到 52 桶" \
  "$(python3 -c "import json;print(len(json.load(open('$WORK/pr_b.body'))['data']['buckets']))")" "52"
chk "档位 B 每桶 total=pass+fail+skip 且 passRate 自洽" \
  "$(python3 -c "
import json
b=json.load(open('$WORK/pr_b.body'))['data']['buckets']
bad=[x for x in b if x['total']!=x['pass']+x['fail']+x['skip'] or (x['total'] and abs(x['passRate']-round(x['pass']/x['total'],4))>1e-9)]
print('ok' if not bad else 'bad=%d'%len(bad))")" "ok"

echo
echo "== 8. 清理 =="
mysql_do 2>/dev/null <<SQL
delete from test_result where run_id in (select id from run where project_id = $PID_);
delete from run where project_id = $PID_;
delete from audit_log where project_id = $PID_;
delete from project where id = $PID_;
delete from api_token where token_hash = '$HASH';
SQL
chk "运行已清空" "$(q "select count(*) from run where project_id = $PID_")" "0"
chk "结果已清空" "$(q "select count(*) from test_result t join run r on r.id=t.run_id where r.project_id=$PID_" 2>/dev/null || echo 0)" "0"
chk "审计已清空" "$(q "select count(*) from audit_log where project_id = $PID_")" "0"
chk "项目已删除" "$(q "select count(*) from project where code like 'EVD-LONGHIST-%'")" "0"
chk "令牌已删除" "$(q "select count(*) from api_token where token_hash='$HASH'")" "0"

echo
echo "== 小结：$PASS PASS / $FAIL FAIL =="
[ "$FAIL" -eq 0 ]
