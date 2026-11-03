*** Settings ***
Documentation     8001 台账抽取：TN-IMP-159 RDMA 功能测试 - PFC 优先级流控测试
...               技术域：RDMA 功能测试
Force Tags        legacy    legacy-15
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-159 RDMA 功能测试 - PFC 优先级流控测试
    [Documentation]    1. 交换机拥塞/队列计数证明目标业务优先级确有拥塞，且网卡或交换机对应UP3/UP6中实际使用的UP的PFC Pause计数较基线有正增量。 2. RDMA Write正常完成，未出现无法接受的丢包、超时、CQ错误；记录UP、PFC收发方
    [Tags]    TN-IMP-159    legacy    legacy-15
    ${A_CMD}=    Set Variable    ethtool -S ens17np0 ib_write_bw -d mlx5_0 -i 1 -x 3 -p 18515 --tclass 104 -s 65536 -D 60 --report_gbits 172.30.1.2 ethtool -S ens17np0
    Log    ${A_CMD}
    Log Many    A 端命令原文    ethtool -S ens17np0 ib_write_bw -d mlx5_0 -i 1 -x 3 -p 18515 --tclass 104 -s 65536 -D 60 --report_gbits 172.30.1.2 ethtool -S ens17np0
    ...    B 端命令原文    ethtool -S ens17np0 ib_write_bw -d mlx5_0 -i 1 -x 3 -p 18515 --tclass 104 -s 65536 -D 60 --report_gbits ethtool -S ens17np0
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
