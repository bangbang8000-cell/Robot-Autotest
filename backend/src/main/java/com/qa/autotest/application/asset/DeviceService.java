package com.qa.autotest.application.asset;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.DeviceRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.asset.Device;
import com.qa.autotest.domain.asset.DevicePort;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import java.util.List;
import org.springframework.stereotype.Service;

/** 设备与端口台账（AST-01）。 */
@Service
public class DeviceService {

    /** 设备写入表单。 */
    public record DeviceCommand(String name, String deviceType, String model, String vendor,
                                String serialNo, String managementIp, String location,
                                String rackNo, String domainTag, boolean enabled, Long ownerId) {
    }

    /** 端口写入表单。 */
    public record PortCommand(String portName, String portType, String protocol, String macAddress,
                              Integer speedGbps, Integer mtu, String ipAddress, String portStatus) {
    }

    private final DeviceRepository devices;
    private final AccessControlService access;
    private final AuditService audit;

    public DeviceService(DeviceRepository devices, AccessControlService access, AuditService audit) {
        this.devices = devices;
        this.access = access;
        this.audit = audit;
    }

    public PageResult<Device> page(long projectId, String keyword, Integer status, String domain,
                                   int page, int size) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.READ);
        return devices.page(projectId, keyword, status, domain, page, size);
    }

    public Device get(long projectId, long id) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.READ);
        return devices.find(projectId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "设备不存在"));
    }

    public List<String> domains(long projectId) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.READ);
        return devices.domains(projectId);
    }

    public Device create(long projectId, DeviceCommand command) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.CREATE);
        requireName(command.name());
        if (command.serialNo() != null && !command.serialNo().isBlank()
                && devices.serialExists(projectId, command.serialNo(), null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "序列号已存在：" + command.serialNo());
        }
        long id = devices.insert(toDevice(null, projectId, command), TenantContext.userId());
        audit.record("device.create", "device", String.valueOf(id), "name=" + command.name());
        return get(projectId, id);
    }

    public Device update(long projectId, long id, DeviceCommand command) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.UPDATE);
        Device existing = devices.find(projectId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "设备不存在"));
        requireName(command.name());
        if (command.serialNo() != null && !command.serialNo().isBlank()
                && devices.serialExists(projectId, command.serialNo(), id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "序列号已存在：" + command.serialNo());
        }
        devices.update(toDevice(id, projectId, command), TenantContext.userId());
        audit.record("device.update", "device", String.valueOf(id), "name=" + command.name());
        return get(projectId, id);
    }

    public void delete(long projectId, long id) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.DELETE);
        devices.find(projectId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "设备不存在"));
        devices.delete(projectId, id);
        audit.record("device.delete", "device", String.valueOf(id), "");
    }

    public List<DevicePort> ports(long projectId, long deviceId) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.READ);
        devices.find(projectId, deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "设备不存在"));
        return devices.ports(projectId, deviceId);
    }

    public DevicePort addPort(long projectId, long deviceId, PortCommand command) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.CREATE);
        devices.find(projectId, deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "设备不存在"));
        requireName(command.portName());
        if (devices.portNameExists(projectId, deviceId, command.portName(), null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "端口名已存在：" + command.portName());
        }
        long id = devices.insertPort(projectId,
                toPort(null, deviceId, command), TenantContext.userId());
        audit.record("device.port-add", "device_port", String.valueOf(id),
                "device=" + deviceId + ",port=" + command.portName());
        return devices.findPort(projectId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "端口不存在"));
    }

    public DevicePort updatePort(long projectId, long portId, PortCommand command) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.UPDATE);
        DevicePort existing = devices.findPort(projectId, portId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "端口不存在"));
        requireName(command.portName());
        if (devices.portNameExists(projectId, existing.deviceId(), command.portName(), portId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "端口名已存在：" + command.portName());
        }
        devices.updatePort(projectId, toPort(portId, existing.deviceId(), command),
                TenantContext.userId());
        audit.record("device.port-update", "device_port", String.valueOf(portId), "");
        return devices.findPort(projectId, portId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "端口不存在"));
    }

    public void deletePort(long projectId, long portId) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.DELETE);
        devices.findPort(projectId, portId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "端口不存在"));
        devices.deletePort(projectId, portId);
        audit.record("device.port-delete", "device_port", String.valueOf(portId), "");
    }

    private static Device toDevice(Long id, long projectId, DeviceCommand c) {
        return new Device(id, projectId, c.name(), c.deviceType(), c.model(), c.vendor(),
                c.serialNo(), c.managementIp(), c.location(), c.rackNo(), c.domainTag(),
                c.enabled(), c.ownerId(), null);
    }

    private static DevicePort toPort(Long id, long deviceId, PortCommand c) {
        return new DevicePort(id, deviceId, c.portName(), c.portType(), c.protocol(),
                c.macAddress(), c.speedGbps(), c.mtu(), c.ipAddress(), c.portStatus());
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "名称不能为空");
        }
    }
}
