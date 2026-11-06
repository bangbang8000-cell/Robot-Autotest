package com.qa.autotest.api.kernel;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.kernel.EnvironmentProbe;
import com.qa.autotest.application.kernel.KernelRegistry;
import com.qa.autotest.application.kernel.KernelSelector;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.kernel.KernelRequest;
import com.qa.autotest.domain.kernel.KernelSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 内核能力 / 选择 / 命令预览（W3）。命令预览用固定 fixture，不接受任意路径。 */
@RestController
@RequestMapping("/api/kernels")
public class KernelController {

    private final KernelRegistry registry;
    private final KernelSelector selector;
    private final EnvironmentProbe probe;
    private final AccessControlService access;

    public KernelController(KernelRegistry registry, KernelSelector selector,
                            EnvironmentProbe probe, AccessControlService access) {
        this.registry = registry;
        this.selector = selector;
        this.probe = probe;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> capabilities() {
        return ApiResponse.ok(probe.capabilities());
    }

    @GetMapping("/select")
    public ApiResponse<KernelSelection> select(@RequestParam long projectId,
                                               @RequestParam(required = false) String setKernel,
                                               @RequestParam(required = false) String caseKernel) {
        access.requireProjectAccess(projectId);
        return ApiResponse.ok(selector.select(projectId, setKernel, caseKernel));
    }

    @GetMapping("/command")
    public ApiResponse<Map<String, Object>> command(@RequestParam long projectId,
                                                    @RequestParam String kernel,
                                                    @RequestParam(defaultValue = "1") int processes) {
        access.requireProjectAccess(projectId);
        KernelRequest request = new KernelRequest(projectId, "PREVIEW", 1, processes,
                List.of("fixtures/smoke/2node-smoke.robot"), List.of(), null, "kernel-preview");
        List<String> argv = registry.require(kernel).command(request, Path.of("artifacts"));
        return ApiResponse.ok(Map.of("kernel", kernel, "argv", argv,
                "source", "PabotCommandBuilder / C4 白名单命令"));
    }
}
