package com.qa.autotest.api.asset;

import com.qa.autotest.application.asset.DeviceService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.asset.Device;
import com.qa.autotest.domain.asset.DevicePort;
import com.qa.autotest.domain.common.PageResult;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 设备与端口台账（AST-01）。 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class DeviceController {

    private final DeviceService devices;

    public DeviceController(DeviceService devices) {
        this.devices = devices;
    }

    @GetMapping("/devices")
    public ApiResponse<PageResult<Device>> page(
            @PathVariable long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(devices.page(projectId, keyword, status, domain, page,
                Math.min(size, 200)));
    }

    @GetMapping("/devices/{id}")
    public ApiResponse<Device> get(@PathVariable long projectId, @PathVariable long id) {
        return ApiResponse.ok(devices.get(projectId, id));
    }

    @GetMapping("/device-domains")
    public ApiResponse<List<String>> domains(@PathVariable long projectId) {
        return ApiResponse.ok(devices.domains(projectId));
    }

    @PostMapping("/devices")
    public ApiResponse<Device> create(@PathVariable long projectId,
                                      @RequestBody DeviceService.DeviceCommand command) {
        return ApiResponse.ok(devices.create(projectId, command));
    }

    @PutMapping("/devices/{id}")
    public ApiResponse<Device> update(@PathVariable long projectId, @PathVariable long id,
                                      @RequestBody DeviceService.DeviceCommand command) {
        return ApiResponse.ok(devices.update(projectId, id, command));
    }

    @DeleteMapping("/devices/{id}")
    public ApiResponse<Void> delete(@PathVariable long projectId, @PathVariable long id) {
        devices.delete(projectId, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/devices/{id}/ports")
    public ApiResponse<List<DevicePort>> ports(@PathVariable long projectId,
                                               @PathVariable long id) {
        return ApiResponse.ok(devices.ports(projectId, id));
    }

    @PostMapping("/devices/{id}/ports")
    public ApiResponse<DevicePort> addPort(@PathVariable long projectId, @PathVariable long id,
                                           @RequestBody DeviceService.PortCommand command) {
        return ApiResponse.ok(devices.addPort(projectId, id, command));
    }

    @PutMapping("/ports/{portId}")
    public ApiResponse<DevicePort> updatePort(@PathVariable long projectId,
                                              @PathVariable long portId,
                                              @RequestBody DeviceService.PortCommand command) {
        return ApiResponse.ok(devices.updatePort(projectId, portId, command));
    }

    @DeleteMapping("/ports/{portId}")
    public ApiResponse<Void> deletePort(@PathVariable long projectId,
                                        @PathVariable long portId) {
        devices.deletePort(projectId, portId);
        return ApiResponse.ok(null);
    }
}
