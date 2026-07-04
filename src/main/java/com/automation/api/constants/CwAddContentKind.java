package com.automation.api.constants;

import com.automation.api.config.EnvironmentConfig;

/**
 * Subscriber continue-watch POST payloads: each kind maps to keys under
 * {@code vrgo.cw.add.<kind>.} in the active {@code environments/<env>.properties}
 * (e.g. {@code vrgo.cw.add.movie.content.id}, {@code vrgo.cw.add.tvshow.content.type}).
 * <p>
 * Switching {@code -P test|dev|stage|prod} or {@code -Denv=} loads a different file so all
 * content UUIDs / types can differ per stack without code changes.
 */
public enum CwAddContentKind {
    MOVIE("movie"),
    TV_SHOW("tvshow"),
    SERIES("series"),
    BOXSET("boxset");

    private final String keySegment;

    CwAddContentKind(String keySegment) {
        this.keySegment = keySegment;
    }

    public String keySegment() {
        return keySegment;
    }

    /** Prefix {@code vrgo.cw.add.tvshow.} etc. */
    public String propertyPrefix() {
        return "vrgo.cw.add." + keySegment + ".";
    }

    public String contentId(EnvironmentConfig config) {
        return config.getProperty(propertyPrefix() + "content.id");
    }

    public String contentType(EnvironmentConfig config, String defaultIfUnset) {
        String v = config.getProperty(propertyPrefix() + "content.type");
        if (v == null || v.isBlank()) {
            return defaultIfUnset;
        }
        return v.strip();
    }

    public int watchDuration(EnvironmentConfig config, int defaultIfUnset) {
        String v = config.getProperty(propertyPrefix() + "watch.duration");
        if (v == null || v.isBlank()) {
            return defaultIfUnset;
        }
        return Integer.parseInt(v.strip());
    }

    /** Default API {@code contentType} when the property is omitted (override per env in properties). */
    public String defaultContentType() {
        return switch (this) {
            case MOVIE, BOXSET -> "VOD";
            case TV_SHOW, SERIES -> "TV_SHOW";
        };
    }

    public int defaultWatchDuration() {
        return 2;
    }
}
