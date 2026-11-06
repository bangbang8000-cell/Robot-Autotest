#!/usr/bin/env bash
# RPT-03 / RPT-04 残余收口：真实库 + 真实 HTTP 上的「双列并置对比」与「通过率/性能趋势」
#
# 原残余：
#   RPT-03 —— 自动/手动基线、跨环境拒绝、跨项目拒绝只在单测里验过，没在真实数据上跑；
#   RPT-04 —— 真实历史数据没接入过，环境分线与 3 倍基线偏离的边界只在单测里。
#
# 做法：在一个一次性项目里造 6 次运行（4 次同环境、1 次跨环境、1 次 started_at 为 NULL），
# 用真实 HTTP 逐条读 compare / baseline-candidates / trend / trend/pass-rate 的返回值，
# 最后把运行、用例结果、项目、令牌全部删掉。
#
# 用法：bash rpt0304b-e2e.sh 2>&1 | tee logs/00-run.log
set -uo pipefail

BASE="http://127.0.0.1:8081"
EVID="$(cd "$(dirname "$0")" && pwd)"
WORK=/tmp/rpt0304b-work
LOGS="$EVID/logs"
TS=$(date +%Y%m%d%H%M%S)
CODE="EVD-RPT0304-$TS"
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

read -r TOKEN HASH <<< "$(python3 - <<'PY'
import hashlib, secrets
t = secrets.token_urlsafe(32)
print(t, hashlib.sha256(t.encode()).hexdigest())
PY
)"
q "insert into api_token (user_id, name, token_hash, token_kind, expires_at)
   values (1, 'rpt0304b-e2e', '$HASH', 'session', now(3) + interval 2 hour)"
chk "临时令牌已建" "$(q "select count(*) from api_token where token_hash='$HASH'")" "1"

get() { # 名称 路径（不含 /api）
  local name=$1 path=$2
  curl -s -o "$WORK/$name.body" -w '%{http_code}' \
    -H "Authorization: Bearer $TOKEN" "$BASE/api$path"
}

# ---------- 断言脚本（JSON 侧） ----------
cat > "$WORK/asserts.py" <<'PY'
import json
import sys


def norm(v):
    if v is True:
        return 'true'
    if v is False:
        return 'false'
    return v


def eq(name, exp, act):
    e, a = norm(exp), norm(act)
    if e == a:
        print('  PASS  %s (%s)' % (name, a))
    else:
        print('  FAIL  %s：期望 %s，实际 %s' % (name, e, a))


def data(p):
    with open(p, encoding='utf-8') as f:
        body = json.load(f)
    if body.get('data') is None:
        print('  FAIL  响应 data 为空（message=%s）' % body.get('message'))
        return None
    return body['data']


def row(rows, key):
    for r in rows:
        if r['caseKey'] == key:
            return r
    return None


def side(s, field):
    return 'null' if s is None else s[field]


def case_auto(d):
    eq('自动基线选中 H3（同环境、最近、早于本轮）', d['baseline']['runId'].endswith('-H3'), True)
    keys = [r['caseKey'] for r in d['rows']]
    eq('对比行数 = 基线∪本轮的 6 个用例', len(keys), 6)
    eq('对比键集合', ','.join(sorted(keys)),
       'Suite.CaseA,Suite.CaseB,Suite.CaseC,Suite.CaseE,Suite.CaseF,Suite.CaseG')
    eq('基线独有的 CaseD 不出现（说明没误选 H2）', 'Suite.CaseD' in keys, False)
    a = row(d['rows'], 'Suite.CaseA')
    eq('A 判定 PASS_TO_FAIL', a['change'], 'PASS_TO_FAIL')
    eq('A 基线侧 verdict', side(a['history'], 'verdict'), 'PASS')
    eq('A 基线侧耗时来自 H3（3000ms）', side(a['history'], 'durationMs'), 3000)
    eq('A 本轮耗时 5000ms', side(a['current'], 'durationMs'), 5000)
    b = row(d['rows'], 'Suite.CaseB')
    eq('B 两侧都失败 → SAME', b['change'], 'SAME')
    c = row(d['rows'], 'Suite.CaseC')
    eq('C 本轮未执行 → NOT_RUN', c['change'], 'NOT_RUN')
    eq('C 本轮侧为 null', c['current'], None)
    eq('C 基线侧仍有记录', side(c['history'], 'durationMs'), 3000)
    e = row(d['rows'], 'Suite.CaseE')
    eq('E 基线无记录但本轮通过 → SAME', e['change'], 'SAME')
    eq('E 基线侧为 null', e['history'], None)
    s = d['summary']
    eq('summary.newFail', s['newFail'], 1)
    eq('summary.bothFail', s['bothFail'], 1)
    eq('summary.notRun', s['notRun'], 1)
    eq('summary.newPass', s['newPass'], 0)


def case_manual(d):
    eq('手动基线选中 H1', d['baseline']['runId'].endswith('-H1'), True)
    a = row(d['rows'], 'Suite.CaseA')
    eq('手动基线下 A 基线耗时为 H1 的 1000ms', side(a['history'], 'durationMs'), 1000)
    eq('手动基线下 A 仍是 PASS_TO_FAIL', a['change'], 'PASS_TO_FAIL')
    c = row(d['rows'], 'Suite.CaseC')
    eq('手动基线下 C 仍为 NOT_RUN', c['change'], 'NOT_RUN')
    s = d['summary']
    eq('手动基线 summary.newFail', s['newFail'], 1)
    eq('手动基线 summary.bothFail', s['bothFail'], 1)
    eq('手动基线 summary.notRun', s['notRun'], 1)
    eq('手动基线 summary.newPass', s['newPass'], 0)


def candidates(d):
    ids = [c['runId'].rsplit('-', 1)[-1] for c in d]
    eq('候选 4 条（H3/H2/H1 + started_at 为空的 NUL）', ','.join(ids), 'H3,H2,H1,NUL')
    eq('不含跨环境 OTH', 'OTH' in ids, False)
    eq('started_at 为空的候选也给出，时间字段为 null', d[-1]['startedAt'], None)


def trend(d, kind):
    if kind == 'a':
        eq('趋势点数 5（含跨环境 1 点）', len(d['points']), 5)
        eq('按时间升序，首点为 H1', d['points'][0]['runId'].endswith('-H1'), True)
        eq('末点为本轮', d['points'][-1]['runId'].endswith('-CUR'), True)
        eq('环境分线 2 条（跨环境不连成一条）', len(d['series']), 2)
        eq('主环境线 4 点', len(d['series'][0]['points']), 4)
        eq('次环境线 1 点', len(d['series'][1]['points']), 1)
        eq('主环境 envKey', d['series'][0]['envKey'], 'DUT=DUT-A;LINK=L0')
        eq('次环境 envKey', d['series'][1]['envKey'], 'DUT=DUT-B;LINK=L9')
        eq('性能基线只取同环境历史 → 3000/2000/1000 的中位数 2000', d['baselineMs'], 2000)
        eq('本轮 5000ms 超过 3 倍基线（6000）→ deviated', d['deviated'], True)
        eq('currentEnvKey', d['currentEnvKey'], 'DUT=DUT-A;LINK=L0')
        eq('deviated 只标在本轮那一个点上', sum(1 for x in d['points'] if x['deviated']), 1)
        marked = [x['runId'].endswith('-CUR') for x in d['points'] if x['deviated']]
        eq('被标 deviated 的正是本轮', marked[0], True)
    elif kind == 'f':
        eq('F 恰好 1.5 倍基线不算偏离（边界严格大于）', d['deviated'], False)
        eq('F 基线 1000ms', d['baselineMs'], 1000)
    elif kind == 'g':
        eq('G 1.501 倍基线算偏离', d['deviated'], True)
    elif kind == 'e':
        eq('E 无任何历史 → baselineMs 为 null', d['baselineMs'], None)
        eq('E 无历史时不给偏离标记', d['deviated'], False)
        eq('E 只有本轮 1 个点', len(d['points']), 1)


def passrate(d, gran):
    eq('granularity 回显', d['granularity'], gran)
    b = d['buckets']
    if gran == 'day':
        eq('按天桶数 5', len(b), 5)
        eq('桶按时间升序', ','.join(x['bucket'] for x in b),
           '2026-09-30,2026-10-01,2026-10-02,2026-10-03,2026-10-04')
        x = [y for y in b if y['bucket'] == '2026-10-03'][0]
        eq('10-03 两天合并计数 pass', x['pass'], 5)
        eq('10-03 fail', x['fail'], 1)
        eq('10-03 total', x['total'], 6)
        eq('10-03 passRate', x['passRate'], 0.8333)
        y = [z for z in b if z['bucket'] == '2026-10-04'][0]
        eq('10-04 passRate', y['passRate'], 0.6)
    else:
        eq('按周桶数 1（跨月也落在同一 ISO 周）', len(b), 1)
        eq('周桶键', b[0]['bucket'], '2026-40')
        eq('周合计 pass', b[0]['pass'], 17)
        eq('周合计 fail', b[0]['fail'], 6)
        eq('周合计 total', b[0]['total'], 23)


if __name__ == '__main__':
    cmd = sys.argv[1]
    d = data(sys.argv[2])
    if d is None:
        sys.exit(0)
    if cmd == 'auto':
        case_auto(d)
    elif cmd == 'manual':
        case_manual(d)
    elif cmd == 'candidates':
        candidates(d)
    elif cmd == 'trend':
        trend(d, sys.argv[3])
    elif cmd == 'passrate':
        passrate(d, sys.argv[3])
PY

pyassert() {
  local out
  out=$(python3 "$WORK/asserts.py" "$@")
  printf '%s\n' "$out"
  PASS=$((PASS + $(printf '%s\n' "$out" | grep -c '^  PASS ')))
  FAIL=$((FAIL + $(printf '%s\n' "$out" | grep -c '^  FAIL ')))
}

echo
echo "== 1. 造数：6 次运行 + 1 次跨项目运行 =="
mysql_do <<SQL
delete from test_result where run_id in (select id from run where project_id in
  (select id from project where code like 'EVD-RPT0304%'));
delete from run where project_id in (select id from project where code like 'EVD-RPT0304%');
delete from project where code like 'EVD-RPT0304%';

insert into project (code, name, description, status, created_by)
values ('$CODE', 'RPT-03/04 残余验证', '一次性验证库，脚本末尾清理', 1, 1);
set @pid := (select id from project where code = '$CODE');

insert into run (run_id, project_id, trigger_type, run_status, started_at, finished_at,
                 dut, link, pass_count, fail_count, skip_count, created_by, created_at) values
 ('$CODE-H1', @pid, 'manual', 'finished', '2026-10-01 10:00:00.000', '2026-10-01 10:05:00.000',
  'DUT-A', 'L0', 4, 1, 0, 1, '2026-10-01 10:00:00.000'),
 ('$CODE-H2', @pid, 'manual', 'finished', '2026-10-02 10:00:00.000', '2026-10-02 10:05:00.000',
  'DUT-A', 'L0', 4, 2, 0, 1, '2026-10-02 10:00:00.000'),
 ('$CODE-H3', @pid, 'manual', 'finished', '2026-10-03 10:00:00.000', '2026-10-03 10:05:00.000',
  'DUT-A', 'L0', 4, 1, 0, 1, '2026-10-03 10:00:00.000'),
 ('$CODE-CUR', @pid, 'manual', 'finished', '2026-10-04 10:00:00.000', '2026-10-04 10:05:00.000',
  'DUT-A', 'L0', 3, 2, 0, 1, '2026-10-04 10:00:00.000'),
 ('$CODE-OTH', @pid, 'manual', 'finished', '2026-10-03 12:00:00.000', '2026-10-03 12:05:00.000',
  'DUT-B', 'L9', 1, 0, 0, 1, '2026-10-03 12:00:00.000'),
 ('$CODE-NUL', @pid, 'manual', 'finished', null, null,
  'DUT-A', 'L0', 1, 0, 0, 1, '2026-09-30 09:00:00.000');

-- H1
insert into test_result (run_id, case_key, verdict, duration_ms) values
 ((select id from run where run_id='$CODE-H1'), 'Suite.CaseA', 'PASS', 1000),
 ((select id from run where run_id='$CODE-H1'), 'Suite.CaseB', 'FAIL', 2000),
 ((select id from run where run_id='$CODE-H1'), 'Suite.CaseC', 'PASS', 3000),
 ((select id from run where run_id='$CODE-H1'), 'Suite.CaseF', 'PASS', 1000),
 ((select id from run where run_id='$CODE-H1'), 'Suite.CaseG', 'PASS', 1000);
-- H2（多一个 CaseD，用来证明自动基线没误选它）
insert into test_result (run_id, case_key, verdict, duration_ms) values
 ((select id from run where run_id='$CODE-H2'), 'Suite.CaseA', 'FAIL', 2000),
 ((select id from run where run_id='$CODE-H2'), 'Suite.CaseB', 'FAIL', 2000),
 ((select id from run where run_id='$CODE-H2'), 'Suite.CaseC', 'PASS', 3000),
 ((select id from run where run_id='$CODE-H2'), 'Suite.CaseD', 'PASS', 500),
 ((select id from run where run_id='$CODE-H2'), 'Suite.CaseF', 'PASS', 1000),
 ((select id from run where run_id='$CODE-H2'), 'Suite.CaseG', 'PASS', 1000);
-- H3
insert into test_result (run_id, case_key, verdict, duration_ms) values
 ((select id from run where run_id='$CODE-H3'), 'Suite.CaseA', 'PASS', 3000),
 ((select id from run where run_id='$CODE-H3'), 'Suite.CaseB', 'FAIL', 2000),
 ((select id from run where run_id='$CODE-H3'), 'Suite.CaseC', 'PASS', 3000),
 ((select id from run where run_id='$CODE-H3'), 'Suite.CaseF', 'PASS', 1000),
 ((select id from run where run_id='$CODE-H3'), 'Suite.CaseG', 'PASS', 1000);
-- CUR：A 由通过转失败、B 两侧都失败、C 本轮没跑、E 全新、F 恰好 1.5 倍、G 1.501 倍
insert into test_result (run_id, case_key, verdict, duration_ms) values
 ((select id from run where run_id='$CODE-CUR'), 'Suite.CaseA', 'FAIL', 5000),
 ((select id from run where run_id='$CODE-CUR'), 'Suite.CaseB', 'FAIL', 2000),
 ((select id from run where run_id='$CODE-CUR'), 'Suite.CaseE', 'PASS', 100),
 ((select id from run where run_id='$CODE-CUR'), 'Suite.CaseF', 'PASS', 1500),
 ((select id from run where run_id='$CODE-CUR'), 'Suite.CaseG', 'PASS', 1501);
-- OTH：同项目但换环境
insert into test_result (run_id, case_key, verdict, duration_ms) values
 ((select id from run where run_id='$CODE-OTH'), 'Suite.CaseA', 'PASS', 9999);
-- NUL：started_at 为空
insert into test_result (run_id, case_key, verdict, duration_ms) values
 ((select id from run where run_id='$CODE-NUL'), 'Suite.CaseA', 'PASS', 1000);

insert into project (code, name, description, status, created_by)
values ('$CODE-X', 'RPT-03 跨项目验证', '一次性验证库', 1, 1);
set @p2 := (select id from project where code = '$CODE-X');
insert into run (run_id, project_id, trigger_type, run_status, started_at, finished_at,
                 dut, link, pass_count, fail_count, skip_count, created_by) values
 ('$CODE-X1', @p2, 'manual', 'finished', '2026-10-03 11:00:00.000',
  '2026-10-03 11:05:00.000', 'DUT-A', 'L0', 1, 0, 0, 1);
insert into test_result (run_id, case_key, verdict, duration_ms) values
 ((select id from run where run_id='$CODE-X1'), 'Suite.CaseA', 'PASS', 1000);
SQL
PID_=$(q "select id from project where code='$CODE'")
P2_=$(q "select id from project where code='$CODE-X'")
chk "专用项目已建" "$([ -n "$PID_" ] && echo ok)" "ok"
chk "同环境运行 6 次" "$(q "select count(*) from run where project_id=$PID_")" "6"
chk "用例结果 23 行" "$(q "select count(*) from test_result t join run r on r.id=t.run_id where r.project_id=$PID_")" "23"

CUR="$CODE-CUR"
H1="$CODE-H1"
OTH="$CODE-OTH"
NUL="$CODE-NUL"
X1="$CODE-X1"

echo
echo "== 2. 自动基线对比 =="
chk "compare(自动) GET 200" "$(get cmp_auto "/reports/$CUR/compare?projectId=$PID_")" "200"
pyassert auto "$WORK/cmp_auto.body"

echo
echo "== 3. 手动基线对比 =="
chk "compare(手动=H1) GET 200" "$(get cmp_man "/reports/$CUR/compare?projectId=$PID_&baselineRunId=$H1")" "200"
pyassert manual "$WORK/cmp_man.body"

echo
echo "== 4. 手动基线候选 =="
chk "baseline-candidates GET 200" "$(get cand "/reports/$CUR/baseline-candidates?projectId=$PID_")" "200"
pyassert candidates "$WORK/cand.body"

echo
echo "== 5. 拒绝与边界 =="
chk "跨环境手动基线 → 400" "$(get cmp_oth "/reports/$CUR/compare?projectId=$PID_&baselineRunId=$OTH")" "400"
chk "拒绝理由点明环境不一致" "$(grep -c '手动基线与本轮环境不一致' "$WORK/cmp_oth.body")" "1"
chk "拒绝理由带出基线环境 DUT-B" "$(grep -c 'DUT-B' "$WORK/cmp_oth.body")" "1"
chk "跨项目手动基线 → 400" "$(get cmp_x "/reports/$CUR/compare?projectId=$PID_&baselineRunId=$X1")" "400"
chk "拒绝理由点明不属于当前项目" "$(grep -c '不存在或不属于当前项目' "$WORK/cmp_x.body")" "1"
chk "started_at 为空的运行作本轮：compare 仍 200" "$(get cmp_nul "/reports/$NUL/compare?projectId=$PID_")" "200"
eq_nul=$(python3 - "$WORK/cmp_nul.body" <<'PY'
import json, sys
d = json.load(open(sys.argv[1], encoding='utf-8'))['data']
print(d['baseline'] is None, len(d['rows']), d['summary']['newFail'], d['summary']['notRun'])
PY
)
chk "started_at 为空时不给自动基线" "$(printf '%s' "$eq_nul" | cut -d' ' -f1)" "True"
chk "started_at 为空时只对比本轮自己那 1 条" "$(printf '%s' "$eq_nul" | cut -d' ' -f2)" "1"

echo
echo "== 6. 用例趋势与性能基线 =="
chk "trend(A) GET 200" "$(get tr_a "/reports/$CUR/trend?projectId=$PID_&caseKey=Suite.CaseA&limit=10")" "200"
pyassert trend "$WORK/tr_a.body" a
chk "trend(F) GET 200" "$(get tr_f "/reports/$CUR/trend?projectId=$PID_&caseKey=Suite.CaseF&limit=10")" "200"
pyassert trend "$WORK/tr_f.body" f
chk "trend(G) GET 200" "$(get tr_g "/reports/$CUR/trend?projectId=$PID_&caseKey=Suite.CaseG&limit=10")" "200"
pyassert trend "$WORK/tr_g.body" g
chk "trend(E) GET 200" "$(get tr_e "/reports/$CUR/trend?projectId=$PID_&caseKey=Suite.CaseE&limit=10")" "200"
pyassert trend "$WORK/tr_e.body" e

echo
echo "== 7. 通过率趋势 =="
chk "pass-rate(day) GET 200" "$(get pr_d "/reports/trend/pass-rate?projectId=$PID_&granularity=day&limit=52")" "200"
pyassert passrate "$WORK/pr_d.body" day
chk "pass-rate(week) GET 200" "$(get pr_w "/reports/trend/pass-rate?projectId=$PID_&granularity=week&limit=52")" "200"
pyassert passrate "$WORK/pr_w.body" week
chk "pass-rate limit 超上限 52 → 400" "$(get pr_over "/reports/trend/pass-rate?projectId=$PID_&granularity=day&limit=53")" "400"
chk "超限理由点明上限" "$(grep -c 'limit 不能超过 52' "$WORK/pr_over.body")" "1"
chk "pass-rate 非法粒度 → 400" "$(get pr_bad "/reports/trend/pass-rate?projectId=$PID_&granularity=month")" "400"

echo
echo "== 8. 跨项目隔离 =="
chk "别项目看本轮 compare → 404" "$(get iso_cmp "/reports/$CUR/compare?projectId=$P2_")" "404"
chk "别项目看本轮 trend → 404" "$(get iso_tr "/reports/$CUR/trend?projectId=$P2_&caseKey=Suite.CaseA")" "404"
chk "别项目看本轮候选 → 404" "$(get iso_cand "/reports/$CUR/baseline-candidates?projectId=$P2_")" "404"
chk "本项目通过率里不出现别项目的运行" "$(q "select count(*) from run where project_id=$P2_ and started_at >= '2026-09-01'")" "1"

echo
echo "== 9. 清理 =="
mysql_do <<SQL
delete from test_result where run_id in (select id from run where project_id in ($PID_, $P2_));
delete from run where project_id in ($PID_, $P2_);
delete from project where id in ($PID_, $P2_);
delete from api_token where token_hash = '$HASH';
SQL
chk "运行已清空" "$(q "select count(*) from run where project_id in ($PID_, $P2_)")" "0"
chk "项目已删除" "$(q "select count(*) from project where code like 'EVD-RPT0304%'")" "0"
chk "令牌已删除" "$(q "select count(*) from api_token where token_hash='$HASH'")" "0"

echo
echo "== 小结：$PASS PASS / $FAIL FAIL =="
[ "$FAIL" -eq 0 ]
