package com.qa.autotest.application.execution;

import java.nio.file.Path;
import java.util.Optional;

/** pabot 版本探针，用于预检版本偏差。 */
public interface PabotVersionProbe {

    Optional<String> version(Path pabot);
}
