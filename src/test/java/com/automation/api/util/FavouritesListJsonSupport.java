package com.automation.api.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.response.Response;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses favourites GET response bodies into row maps (separate from continue-watch list parsing),
 * including {@code GET /favourites/channels} shapes.
 */
public final class FavouritesListJsonSupport {

    private FavouritesListJsonSupport() {
    }

    /**
     * Normalizes GET favourites {@code data} to a list of row maps (favourites-specific wrapper keys first).
     */
    public static List<Map<String, Object>> listRowsFromGetResponse(Response r) {
        for (String path : List.of(
                "data.favouriteContentHistories",
                "data.favourites",
                "data.contentHistories")) {
            List<Map<String, Object>> fromPath = mapsFromJsonPathList(r, path);
            if (!fromPath.isEmpty()) {
                return fromPath;
            }
        }

        Object data = r.jsonPath().get("data");
        if (data == null) {
            return List.of();
        }
        if (data instanceof List<?>) {
            return rowsAsMaps((List<?>) data);
        }
        Map<String, Object> dataMap = tryToPropertyMap(data);
        if (dataMap == null) {
            return List.of();
        }
        List<?> raw = extractFavouritesItemListFromDataMap(dataMap);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return rowsAsMaps(raw);
    }

    /**
     * Parses GET {@code /favourites/channels} response: tries channel-specific {@code data.*} arrays, then
     * falls back to the same shapes as {@link #listRowsFromGetResponse(Response)}.
     */
    public static List<Map<String, Object>> listRowsFromChannelsGetResponse(Response r) {
        for (String path : List.of(
                "data.channels",
                "data.favouriteChannels",
                "data.channelFavourites",
                "data.favourites",
                "data.favouriteContentHistories",
                "data.contentHistories",
                "data.items")) {
            List<Map<String, Object>> fromPath = mapsFromJsonPathList(r, path);
            if (!fromPath.isEmpty()) {
                return fromPath;
            }
        }
        return listRowsFromGetResponse(r);
    }

    /** Whether the favourite-channels GET includes the configured channel editorial id (parsed rows or raw body). */
    public static boolean channelsResponseContainsContentId(Response r, String channelContentId) {
        if (channelContentId == null || channelContentId.isBlank()) {
            return false;
        }
        String c = channelContentId.strip();
        for (Map<String, Object> row : listRowsFromChannelsGetResponse(r)) {
            String id = rowContentId(row);
            if (id != null && contentIdsMatch(id, c)) {
                return true;
            }
        }
        return r.asString().contains(c);
    }

    /**
     * Top-level list row {@code id} for GET favourites presence/absence checks.
     * Ignores other keys (e.g. {@code nextPlayableContentId}) and nested editorial objects.
     */
    public static String rowListItemId(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object v = row.get("id");
        if (v != null && !String.valueOf(v).isBlank()) {
            return String.valueOf(v).strip();
        }
        return null;
    }

    /** All top-level {@code id} values from parsed GET favourites rows (deduped, insertion order). */
    public static Set<String> collectListItemIdsFromGetResponse(Response r) {
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Object> row : listRowsFromGetResponse(r)) {
            String id = rowListItemId(row);
            if (id != null && !id.isBlank()) {
                out.add(id);
            }
        }
        return out;
    }

    /** Editorial / asset id for DELETE path (same key order as favourites row shapes). */
    public static String rowContentId(Map<String, Object> row) {
        for (Map<String, Object> layer : rowLookupLayers(row)) {
            for (String key : List.of("contentId", "id", "content_id", "entityId", "assetId")) {
                Object v = layer.get(key);
                if (v != null) {
                    return String.valueOf(v);
                }
            }
        }
        return null;
    }

    /** All {@link #rowContentId(Map)} values from parsed rows (deduped, insertion order). */
    public static Set<String> collectContentIdsFromGetResponse(Response r) {
        return collectContentIdsFromRows(listRowsFromGetResponse(r));
    }

    public static Set<String> collectContentIdsFromRows(List<Map<String, Object>> rows) {
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String id = rowContentId(row);
            if (id != null && !id.isBlank()) {
                out.add(id.strip());
            }
        }
        return out;
    }

    /** Whether the GET response lists {@code configuredContentId} as a row's top-level {@code id}. */
    public static boolean responseContainsContentId(Response r, String configuredContentId) {
        if (configuredContentId == null || configuredContentId.isBlank()) {
            return false;
        }
        String c = configuredContentId.strip();
        for (String rid : collectListItemIdsFromGetResponse(r)) {
            if (listItemIdsMatch(rid, c)) {
                return true;
            }
        }
        return false;
    }

    /** True if the failed-TV id is not listed as a favourite (negative path). */
    public static boolean responseExcludesContentId(Response r, String configuredContentId) {
        return !responseContainsContentId(r, configuredContentId);
    }

    private static boolean listItemIdsMatch(String responseId, String configuredId) {
        if (responseId == null || configuredId == null) {
            return false;
        }
        return responseId.strip().equalsIgnoreCase(configuredId.strip());
    }

    private static boolean contentIdsMatch(String responseId, String configuredId) {
        if (responseId == null || configuredId == null) {
            return false;
        }
        String r = responseId.strip();
        String c = configuredId.strip();
        if (r.equalsIgnoreCase(c)) {
            return true;
        }
        return r.endsWith(c) || c.endsWith(r) || r.contains(c) || c.contains(r);
    }

    /** Query/content type hint (e.g. LIVE, VOD); defaults to {@code VOD} when absent (matches VRGO list rows). */
    public static String rowContentType(Map<String, Object> row) {
        for (Map<String, Object> layer : rowLookupLayers(row)) {
            Object v = layer.get("contentType");
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v).strip();
            }
        }
        return "VOD";
    }

    private static List<Map<String, Object>> mapsFromJsonPathList(Response r, String path) {
        List<?> loose = toLooseList(r.jsonPath().get(path));
        if (loose == null || loose.isEmpty()) {
            return List.of();
        }
        return rowsAsMaps(loose);
    }

    private static List<Map<String, Object>> rowsAsMaps(List<?> raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : raw) {
            Map<String, Object> row = toStringKeyedMap(o);
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }

    private static List<?> extractFavouritesItemListFromDataMap(Map<String, Object> dataMap) {
        for (String key : List.of(
                "favouriteContentHistories",
                "favourites",
                "contentHistories",
                "items",
                "records",
                "list",
                "content",
                "histories",
                "data")) {
            List<?> loose = toLooseList(dataMap.get(key));
            if (loose != null && !loose.isEmpty()) {
                return loose;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> rowLookupLayers(Map<String, Object> row) {
        List<Map<String, Object>> layers = new ArrayList<>();
        layers.add(row);
        for (String nested : List.of("contentEditorial", "content", "editorial", "metadata")) {
            Object o = row.get(nested);
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                layers.add(m);
            } else if (o instanceof JsonNode jn && jn.isObject()) {
                layers.add(JsonUtils.mapper().convertValue(jn, new TypeReference<Map<String, Object>>() { }));
            }
        }
        return layers;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tryToPropertyMap(Object data) {
        if (data instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (data instanceof JsonNode node && node.isObject()) {
            return JsonUtils.mapper().convertValue(node, new TypeReference<Map<String, Object>>() { });
        }
        return null;
    }

    private static List<?> toLooseList(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            return list;
        }
        if (v instanceof JsonNode node && node.isArray()) {
            List<Object> list = new ArrayList<>(node.size());
            node.forEach(list::add);
            return list;
        }
        if (v instanceof Collection<?> c) {
            return new ArrayList<>(c);
        }
        if (v.getClass().isArray()) {
            int n = Array.getLength(v);
            List<Object> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(Array.get(v, i));
            }
            return list;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringKeyedMap(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (o instanceof JsonNode node && node.isObject()) {
            return JsonUtils.mapper().convertValue(node, new TypeReference<Map<String, Object>>() { });
        }
        return null;
    }
}
