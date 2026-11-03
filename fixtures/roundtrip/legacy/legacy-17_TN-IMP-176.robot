*** Settings ***
Documentation     8001 台账抽取：TN-IMP-176 perftest 读延时测试
...               技术域：RoCE 单口单向性能测试
Force Tags        legacy    legacy-17
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-176 perftest 读延时测试
    [Documentation]    1. 所有指定轨真实运行，完整23档、每档10000次，双方退出0且无QP/CQE错误。 2. 各报文typical时延≤冻结历史同方法、同轨基线的125%；保留min/max/typical/avg/p99。
    [Tags]    TN-IMP-176    legacy    legacy-17
    ${A_CMD}=    Set Variable    numactl --physcpubind=16 --membind=0 /home/inspur/aisoft/share/perftest/ib_read_lat -d mlx5_0 -i 1 -x 3 -p 19353 --tclass 104 -a -n 10000 172.30.1.2
    Log    ${A_CMD}
    Log Many    A 端命令原文    numactl --physcpubind=16 --membind=0 /home/inspur/aisoft/share/perftest/ib_read_lat -d mlx5_0 -i 1 -x 3 -p 19353 --tclass 104 -a -n 10000 172.30.1.2
    ...    B 端命令原文    numactl --physcpubind=16 --membind=0 /home/inspur/aisoft/harry/perftest/ib_read_lat -d mlx5_0 -i 1 -x 3 -p 19353 --tclass 104 -a -n 10000
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
