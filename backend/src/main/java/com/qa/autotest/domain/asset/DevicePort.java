package com.qa.autotest.domain.asset;

/** 设备端口（FR-v0.2-10）：端口是可被引用的实体，v0.4 租约的外键。 */
public record DevicePort(
        Long id,
        Long deviceId,
        String portName,
        String portType,
        String protocol,
        String macAddress,
        Integer speedGbps,
        Integer mtu,
        String ipAddress,
        String portStatus) {
}
