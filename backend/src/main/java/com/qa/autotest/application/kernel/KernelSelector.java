package com.qa.autotest.application.kernel;

import com.qa.autotest.application.repository.SystemSettingRepository;
import com.qa.autotest.application.system.SystemSettingService;
import com.qa.autotest.domain.kernel.KernelSelection;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 选择粒度（KRN-04）：项目默认 → 用例集覆盖 → 用例覆盖，后者优先；
 * 每次返回「生效来源」与「是否被覆盖」，页面上必须能解释为什么跑到这个内核。
 */
@Service
public class KernelSelector {

    private final KernelRegistry registry;
    private final SystemSettingRepository settings;

    public KernelSelector(KernelRegistry registry, SystemSettingRepository settings) {
        this.registry = registry;
        this.settings = settings;
    }

    public KernelSelection select(long projectId, String setOverride, String caseOverride) {
        String projectDefault = settings.findProject(projectId,
                        SystemSettingService.P_DEFAULT_KERNEL)
                .orElse(RobotKernel.NAME)
                .toLowerCase(Locale.ROOT);
        String chosen = choose(projectDefault, setOverride, caseOverride);
        registry.require(chosen);
        String source = caseOverride != null && !caseOverride.isBlank() ? "CASE_OVERRIDE"
                : setOverride != null && !setOverride.isBlank() ? "SET_OVERRIDE"
                : "PROJECT_DEFAULT";
        return new KernelSelection(chosen, source, !"PROJECT_DEFAULT".equals(source));
    }

    private static String choose(String projectDefault, String setOverride, String caseOverride) {
        if (caseOverride != null && !caseOverride.isBlank()) {
            return caseOverride.trim().toLowerCase(Locale.ROOT);
        }
        if (setOverride != null && !setOverride.isBlank()) {
            return setOverride.trim().toLowerCase(Locale.ROOT);
        }
        return projectDefault;
    }
}
