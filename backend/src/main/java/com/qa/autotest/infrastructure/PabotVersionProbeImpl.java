package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.PabotVersionProbe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** 执行 pabot --version，10 秒超时。 */
public class PabotVersionProbeImpl implements PabotVersionProbe {

    @Override
    public Optional<String> version(Path pabot) {
        try {
            Process process = new ProcessBuilder(pabot.toString(), "--version")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line == null || line.isBlank()) {
                    return Optional.empty();
                }
                String cleaned = line.trim();
                int space = cleaned.lastIndexOf(' ');
                return Optional.of(space >= 0 ? cleaned.substring(space + 1) : cleaned);
            }
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
