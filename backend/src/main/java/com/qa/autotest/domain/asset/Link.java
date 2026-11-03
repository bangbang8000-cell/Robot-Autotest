package com.qa.autotest.domain.asset;

/**
 * 链路（FR-v0.2-11）：两端设备与端口都记，构成 {@code DUT — LINK — HOST} 三元链。
 *
 * <p>反查时一次查询取全两端名称，避免前端逐级请求。
 */
public record Link(
        Long id,
        Long projectId,
        String name,
        String linkType,
        String protocol,
        Integer speedGbps,
        Integer mtu,
        Long aDeviceId,
        String aDeviceName,
        Long aPortId,
        String aPortName,
        Long bDeviceId,
        String bDeviceName,
        Long bPortId,
        String bPortName,
        String description,
        boolean enabled) {
}
