package com.automation.api.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class PropertyLoader {

    private PropertyLoader() {
    }

    public static Properties loadClasspath(String classpathLocation) {
        Properties p = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + classpathLocation);
            }
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + classpathLocation, e);
        }
        return p;
    }
}
