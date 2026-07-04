package com.automation.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists rotated {@code refresh_token} back to {@code secrets/vrgo-auth.local.properties}
 * so you do not need to copy from the browser after every successful test run.
 */
final class VrgoAuthSecretsWriter {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoAuthSecretsWriter.class);

    private VrgoAuthSecretsWriter() {
    }

    static void persistRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        Path path = VrgoAuthSecretsLoader.resolveLocalSecretsPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>();
            boolean replaced = false;
            for (String line : lines) {
                if (line.startsWith("vrgo.refresh.token=")) {
                    updated.add("vrgo.refresh.token=" + refreshToken.strip());
                    replaced = true;
                } else {
                    updated.add(line);
                }
            }
            if (!replaced) {
                updated.add("vrgo.refresh.token=" + refreshToken.strip());
            }
            Files.write(path, updated, StandardCharsets.UTF_8);
            LOG.info("Updated refresh_token in {}", path.toAbsolutePath());
        } catch (IOException e) {
            LOG.warn("Could not update {}: {}", path, e.getMessage());
        }
    }
}
