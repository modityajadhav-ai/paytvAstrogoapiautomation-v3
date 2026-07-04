package com.automation.api.constants;

import com.automation.api.config.EnvironmentConfig;

/**
 * Logical VRGO content types. Resolved from {@code vrgo.content.<suffix>} in the active
 * {@code environments/<env>.properties} file, falling back to the default id when unset.
 */
public enum VrgoContentKind {
    MOVIE("movie", "Movie"),
    TV_SHOW("tvshow", "TVShow"),
    SERIES("series", "Series"),
    BOXSET("boxset", "Boxset");

    private final String propertySuffix;
    private final String defaultId;

    VrgoContentKind(String propertySuffix, String defaultId) {
        this.propertySuffix = propertySuffix;
        this.defaultId = defaultId;
    }

    public String propertyKey() {
        return "vrgo.content." + propertySuffix;
    }

    /**
     * Value from properties, or {@link #defaultId} if the key is missing or blank.
     */
    public String resolve(EnvironmentConfig config) {
        String v = config.getProperty(propertyKey());
        return (v != null && !v.isBlank()) ? v.strip() : defaultId;
    }
}
