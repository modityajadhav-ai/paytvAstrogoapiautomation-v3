package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * VRGO homescreen-service: public menu list, rail hierarchy (list + per-rail details), rail detail, see-all, purchased-rail.
 * <p>
 * Auth and static headers match other VRGO clients: {@code vrgo.bearer.token} / {@code VRGO_BEARER_TOKEN},
 * {@code vrgo.x.api.key} / env, and {@code vrgo.header.*} from the active environment file.
 */
public class HomescreenApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";

    private final String menuListPath;
    private final String railHierarchyPath;
    private final String railHierarchyDetailsPath;
    private final String railByIdPath;
    private final String seeAllPath;
    private final String purchasedRailPath;
    private final String footersPath;
    private final String defaultPlatformId;

    public HomescreenApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.menuListPath = config.getProperty(
                "vrgo.homescreen.menu.list.path",
                "/homescreen-service/pub/v2/menu/list/platformId"
        );
        this.railHierarchyPath = config.getProperty(
                "vrgo.homescreen.rail.hierarchy.path",
                "/homescreen-service/pub/v1/rail-hierarchy/{linkToPageId}"
        );
        this.railHierarchyDetailsPath = config.getProperty(
                "vrgo.homescreen.rail.hierarchy.details.path",
                "/homescreen-service/pub/v1/rail-hierarchy/{railId}/details"
        );
        this.railByIdPath = config.getProperty(
                "vrgo.homescreen.rail.by.id.path",
                "/homescreen-service/pub/v1/rail/{railId}"
        );
        this.seeAllPath = config.getProperty(
                "vrgo.homescreen.see.all.path",
                "/homescreen-service/pub/v1/see-all/{railId}"
        );
        this.purchasedRailPath = config.getProperty(
                "vrgo.homescreen.purchased.rail.path",
                "/homescreen-service/pub/v1/purchased-rail"
        );
        this.footersPath = config.getProperty(
                "vrgo.homescreen.footers.path",
                "/homescreen-service/pub/v1/footers/{platformId}"
        );
        this.defaultPlatformId = config.getProperty("vrgo.homescreen.menu.platform.id", "");
    }

    /**
     * GET {@code /pub/v2/menu/list/platformId?platformId=...} (homescreen-service).
     * When {@code platformId} is null or blank, uses {@code vrgo.homescreen.menu.platform.id} from config.
     */
    public Response getMenuListRaw(String platformId) {
        String id = (platformId != null && !platformId.isBlank()) ? platformId : defaultPlatformId;
        if (id == null || id.isBlank()) {
            throw new IllegalStateException(
                    "Pass platformId or set vrgo.homescreen.menu.platform.id in the active environment file."
            );
        }
        return vrgoGiven()
                .queryParam("platformId", id)
                .when()
                .get(menuListPath);
    }

    /**
     * GET {@code /pub/v1/rail-hierarchy/{linkToPageId}?limit=...&offset=...} (homescreen-service).
     */
    public Response getRailHierarchyRaw(String linkToPageId, int limit, int offset) {
        if (linkToPageId == null || linkToPageId.isBlank()) {
            throw new IllegalStateException("linkToPageId must be non-blank.");
        }
        return vrgoGiven()
                .pathParam("linkToPageId", linkToPageId.strip())
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .when()
                .get(railHierarchyPath);
    }

    /**
     * GET {@code /pub/v1/rail-hierarchy/{railId}/details} (homescreen-service) — rail-level detail for a hierarchy rail id.
     */
    public Response getRailHierarchyDetailsRaw(String railId) {
        if (railId == null || railId.isBlank()) {
            throw new IllegalStateException("railId must be non-blank.");
        }
        return vrgoGiven()
                .pathParam("railId", railId.strip())
                .when()
                .get(railHierarchyDetailsPath);
    }

    /**
     * GET {@code /pub/v1/rail/{railId}?offset=...&limit=...&entitlementFilteringEnabled=...} (homescreen-service).
     */
    public Response getRailByIdRaw(String railId, int offset, int limit, boolean entitlementFilteringEnabled) {
        if (railId == null || railId.isBlank()) {
            throw new IllegalStateException("railId must be non-blank.");
        }
        return vrgoGiven()
                .pathParam("railId", railId.strip())
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .queryParam("entitlementFilteringEnabled", entitlementFilteringEnabled)
                .when()
                .get(railByIdPath);
    }

    /**
     * GET {@code /pub/v1/see-all/{railId}?offset=...&limit=...&sort=...} (homescreen-service).
     * When {@code sort} is null or blank, no {@code sort} query param is sent.
     */
    public Response getSeeAllRaw(String railId, int offset, int limit, String sort) {
        if (railId == null || railId.isBlank()) {
            throw new IllegalStateException("railId must be non-blank.");
        }
        RequestSpecification req = vrgoGiven()
                .pathParam("railId", railId.strip())
                .queryParam("offset", offset)
                .queryParam("limit", limit);
        if (sort != null && !sort.isBlank()) {
            req = req.queryParam("sort", sort.strip());
        }
        return req.when().get(seeAllPath);
    }

    /**
     * GET {@code /pub/v1/purchased-rail?limit=...&offset=...&contentType=...&isEntitlementEnabled=...} (homescreen-service).
     * {@code contentType} is typically a comma-separated list (e.g. {@code VOD,BOXSET}).
     */
    public Response getPurchasedRailRaw(int limit, int offset, String contentType, boolean isEntitlementEnabled) {
        RequestSpecification req = vrgoGiven()
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("isEntitlementEnabled", isEntitlementEnabled);
        if (contentType != null && !contentType.isBlank()) {
            req = req.queryParam("contentType", contentType.strip());
        }
        return req.when().get(purchasedRailPath);
    }

    /**
     * GET {@code /pub/v1/footers/{platformId}} (homescreen-service).
     * When {@code platformHeader} is non-blank, it replaces the {@code platform} value from {@code vrgo.header.*}.
     */
    public Response getFootersRaw(String platformId, String platformHeader) {
        if (platformId == null || platformId.isBlank()) {
            throw new IllegalStateException("platformId must be non-blank.");
        }
        return vrgoGiven(platformHeader)
                .pathParam("platformId", platformId.strip())
                .when()
                .get(footersPath);
    }

    private RequestSpecification vrgoGiven() {
        return vrgoGiven(null);
    }

    private RequestSpecification vrgoGiven(String platformOverride) {
        RequestSpecification r = given().spec(spec);

        String token = VrgoAuthSupport.getBearerToken(environmentConfig);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Set vrgo.bearer.token (e.g. via test base setup), VRGO_BEARER_TOKEN, or -Dvrgo.bearer.token."
            );
        }
        String authorization = token.regionMatches(true, 0, "Bearer ", 0, 7) ? token : "Bearer " + token;
        r = r.header("Authorization", authorization);

        String apiKey = firstNonBlank(
                System.getProperty("vrgo.x.api.key"),
                System.getenv("VRGO_X_API_KEY"),
                environmentConfig.getProperty("vrgo.x.api.key"));
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Set vrgo.x.api.key (properties / test base / env), VRGO_X_API_KEY, or -Dvrgo.x.api.key."
            );
        }
        r = r.header("x-api-key", apiKey);

        Map<String, String> staticHeaders = new java.util.LinkedHashMap<>(
                environmentConfig.propertiesWithPrefix(VRGO_HEADER_PREFIX)
        );
        if (platformOverride != null && !platformOverride.isBlank()) {
            staticHeaders.put("platform", platformOverride.strip());
        }
        for (Map.Entry<String, String> e : staticHeaders.entrySet()) {
            r = r.header(e.getKey(), e.getValue());
        }
        return r;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
