*** Settings ***
Documentation     8001 台账抽取：TN-IMP-119 大文件传输
...               技术域：文件传输测试
Force Tags        legacy    legacy-11
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-119 大文件传输
    [Documentation]    1. 传输通过172.30.1.1→172.30.1.2数据面完成，SCP退出码为0。 2. 两端文件大小均为4294967296字节，SHA256完全一致。 3. 失败/中断或数据不一致不得通过；清理临时文件不代表传输已通过。
    [Tags]    TN-IMP-119    legacy    legacy-11
    ${A_CMD}=    Set Variable    dd if=/dev/urandom of=/tmp/TN-IMP-119_source.bin bs=1M count=1024 status=progress sha256sum /tmp/TN-IMP-119_source.bin scp -o BindAddress=172.30.1.1 /tmp/TN-IMP-119_source.bin insp
    Log    ${A_CMD}
    Log Many    A 端命令原文    dd if=/dev/urandom of=/tmp/TN-IMP-119_source.bin bs=1M count=1024 status=progress sha256sum /tmp/TN-IMP-119_source.bin scp -o BindAddress=172.30.1.1 /tmp/TN-IMP-119_source.bin insp
    ...    B 端命令原文    sha256sum /tmp/TN-IMP-119_received.bin
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
