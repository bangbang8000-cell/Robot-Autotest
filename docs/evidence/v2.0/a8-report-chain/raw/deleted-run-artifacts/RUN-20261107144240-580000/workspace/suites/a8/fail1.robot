*** Settings ***
Documentation     A8 报告链路取证夹具
Metadata          CASE_ID      A8-RPT-FAIL1
Metadata          RUN_ID       A8-RPT-FAIL1-20261107144236
Metadata          QA_PROJECT   a8-report-fixture
Metadata          DUT          LOCAL-HOST
Metadata          LINK         LOCAL
Metadata          SW_BUILD     v2.1-a8
Metadata          BASELINE     n/a
Metadata          OPERATOR     大师
Metadata          STARTED_AT   2026-11-07T06:41:39Z
Metadata          CASE_KIND    report-chain

*** Test Cases ***

A8 PASS 01
    Log    a8 pass 01
A8 FAIL 01
    Fail    A8 失败样本（验证失败分布）
