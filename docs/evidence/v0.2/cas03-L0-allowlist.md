# L0 只读用例清单（开发期唯一允许在真实设备执行的真实用例）

> 来源：`基本功能导入测试v0.2.6.0.1.xlsx`「详细测试流程」页，226 条中机检通过 **10 条**。
> 判据见 `docs/adr/ADR-0013-执行安全分级与开发期禁跑令.md`；机检脚本 `tools/classify_cases.py`。
> **机检只是初筛。按 ADR-0013 D8，必须人工逐条看过命令原文并签字，才能放进开发期使用。**

## 复核方式（5 分钟）

只看一件事：**这一列有没有会改设备状态的动作？**（写网卡参数、断链路、重启、改交换机、断电）
有 → 划掉；没有 → 签「可」。

| # | 用例编号 | 标题 | 命令原文 | 预期（摘要） | 人工复核 |
|---:|---|---|---|---|---|
| 1 | `TN-IMP-002` | BMC 识别 - SMBus/I2C 链路硬件识别 | `ssh admin@10.202.54.11 "diagnose i2cdetect -l"` | 1. BMC能够在正确的SMBus/I2C通道发现目标CX7对应的设备地址，并取得有效的设备识别信息 | ☐ |
| 2 | `TN-IMP-016` | 多型号CPU服务器兼容测试 | `lscpu<br>lspci -Dnnk -d 15b3:1021<br>ping -c 3 -I ens17np0 172.30.1.2` | 1. 至少两种实际参测CPU型号均已留下CPU识别证据。 2. 每个平台均能枚举目标CX7、显示预期 | ☐ |
| 3 | `TN-IMP-017` | 操作系统兼容性测试 | `cat /etc/os-release<br>uname -r<br>lspci -Dnnk -d 15b3:1021<br>ping -c 3 -I ens17np0 172.30.1.2` | 1. 每个实际参测OS均可正常启动，OS版本与目标清单一致。 2. 对应环境下CX7可正确枚举并绑定 | ☐ |
| 4 | `TN-IMP-021` | 网卡PCIE信息检查 | `sudo lspci -Dnnvv -s 0000:45:00.0` | 1. 可读取目标CX7的Vendor/Device ID及PCIe能力和控制信息。 2. LnkSt | ☐ |
| 5 | `TN-IMP-025` | 光模块数字诊断指标验证 | `sudo ethtool -m ens17np0` | 1. 能正常读取光模块DOM/DDM数据，显示温度、电压及模块实际支持的TX/RX光功率指标。 2. | ☐ |
| 6 | `TN-IMP-027` | 光模块信息检查测试 | `sudo ethtool -m ens17np0` | 1. 能读取模块厂商（Vendor）、料号（PN）、序列号（SN）及模块类型等EEPROM身份信息。 | ☐ |
| 7 | `TN-IMP-029` | 与其它型号网卡混用测试 | `ethtool -i ens17np0<br>ethtool -i ens16f0<br>ping -c 4 -I ens17np0 172.30.1.2<br>ping -c 4 -I ens16f0 172.30.11.2` | 1. CX7绑定mlx5_core，图南网卡绑定其对应驱动，网卡和驱动均正常识别，无驱动互相覆盖或加 | ☐ |
| 8 | `TN-IMP-046` | 指示灯功能测试 | `ethtool ens17np0` | 用户人工核对指示灯测试无问题，确认通过；人工确认来源单独归档。 | ☐ |
| 9 | `TN-IMP-048` | Advertised Link Modes查询 | `ethtool ens17np0` | 1. ethtool 成功输出接口信息，并能读取 Advertised link modes 或明确 | ☐ |
| 10 | `TN-IMP-049` | 端口介质类型识别 | `sudo ethtool -m ens17np0<br>ethtool ens17np0` | 1. 能读出网卡 Port/Supported ports 等可用接口信息。 2. 对支持 EEPR | ☐ |

**复核结论**：☐ 全部可用　☐ 剔除第 ___ 条（原因：____________）　签字：__________　日期：________

## 使用约束

1. 只在**临时项目**里用，不写进正式用例库执行；
2. 一次不超过 3 条，逐条看输出；
3. 任一条输出异常，**停止**并记录，不要继续往下跑；
4. 这 10 条都要求 DUT 在线 —— DUT 不可达时不许用「模拟数据」冒充跑通。
