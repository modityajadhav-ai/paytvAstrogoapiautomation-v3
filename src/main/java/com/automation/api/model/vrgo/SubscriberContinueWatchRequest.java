package com.automation.api.model.vrgo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Body for POST {@code /subscriber-activity-producer/v3/subscriber-continue-watch}.
 * {@code groupKey} is required for linear / CDVR-style events (e.g. channel-day {@code eventId}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubscriberContinueWatchRequest(
        String contentId,
        String contentType,
        int watchDuration,
        String subscriberId,
        String groupKey
) {
    public SubscriberContinueWatchRequest(
            String contentId,
            String contentType,
            int watchDuration,
            String subscriberId
    ) {
        this(contentId, contentType, watchDuration, subscriberId, null);
    }
}
