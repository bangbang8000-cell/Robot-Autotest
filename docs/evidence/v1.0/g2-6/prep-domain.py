#!/usr/bin/env python3
"""G2-6 走查前数据准备：给 dev 库的一批用例打技术域标注（台账无「技术域」列，落地后全为空）。"""
import json
import pathlib
import urllib.request

BASE = "http://127.0.0.1:8081/api"
PWD = pathlib.Path.home().joinpath(".config/robot-autotest/qa-ui-pwd").read_text().strip()
PROJECT = 1


def call(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode() or "{}")


st, login = call("POST", "/auth/login", body={"username": "qa-ui", "password": PWD})
print("LOGIN", st, "roles=", login["data"]["user"]["roles"], "expiresAt=", login["data"]["expiresAt"])
tok = login["data"]["token"]

st, page = call("GET", f"/projects/{PROJECT}/cases?page=1&size=20", tok)
items = page["data"]["items"]
print("PAGE", st, "total=", page["data"]["total"], "returned=", len(items))
print("BEFORE first 5:", [(i["id"], i["caseCode"], i.get("domainTag")) for i in items[:5]])

roce_ids = [i["id"] for i in items[:10]]
storage_ids = [i["id"] for i in items[10:15]]
print("roce_ids", roce_ids)
print("storage_ids", storage_ids)

for tag, ids in (("roce", roce_ids), ("storage", storage_ids)):
    st, res = call("POST", f"/projects/{PROJECT}/cases/batch", tok,
                   {"action": "setDomainTag", "ids": ids, "domainTag": tag})
    d = res["data"]
    print(f"BATCH setDomainTag={tag}", st, "total=", d["total"], "succeeded=", d["succeeded"], "failed=", d["failed"])

for tag in ("roce", "storage", "no-such-domain", ""):
    st, res = call("GET", f"/projects/{PROJECT}/cases?page=1&size=50&domain={tag}", tok)
    d = res["data"]
    codes = [i["caseCode"] for i in d["items"]]
    print(f"FILTER domain='{tag}'", st, "total=", d["total"], "codes=", codes[:12])

st, res = call("GET", f"/projects/{PROJECT}/device-domains", tok)
print("DEVICE-DOMAINS", st, res["data"])

st, res = call("GET", f"/projects/{PROJECT}/devices?page=1&size=50&domain=roce", tok)
print("DEVICES domain=roce", st, "total=", res["data"]["total"],
      [(i["name"], i.get("domainTag")) for i in res["data"]["items"]])
st, res = call("GET", f"/projects/{PROJECT}/devices?page=1&size=50&domain=storage", tok)
print("DEVICES domain=storage", st, "total=", res["data"]["total"])
