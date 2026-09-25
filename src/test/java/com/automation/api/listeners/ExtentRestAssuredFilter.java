package com.automation.api.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.markuputils.CodeLanguage;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.Markup;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import io.qameta.allure.attachment.http.HttpRequestAttachment;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.internal.NameAndValue;
import io.restassured.internal.support.Prettifier;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static io.qameta.allure.attachment.http.HttpRequestAttachment.Builder.create;

/**
 * REST Assured filter that logs HTTP request/response details (including cURL) to the
 * active Extent test node — similar to {@link io.qameta.allure.restassured.AllureRestAssured}.
 */
public class ExtentRestAssuredFilter implements OrderedFilter {

    private static final String HIDDEN_PLACEHOLDER = "[ BLACKLISTED ]";
    private static final int MAX_BODY_CHARS = 64 * 1024;

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                           FilterableResponseSpecification responseSpec,
                           FilterContext filterContext) {
        ExtentTest test = ExtentReportManager.getCurrentTest();

        if (test != null) {
            try {
                logRequest(test, buildRequestAttachment(requestSpec));
            } catch (RuntimeException ignored) {
                // Never fail tests due to reporting
            }
        }

        Response response = filterContext.next(requestSpec, responseSpec);

        if (test != null) {
            try {
                logResponse(test, requestSpec, response);
            } catch (RuntimeException ignored) {
                // Never fail tests due to reporting
            }
        }

        return response;
    }

    private static HttpRequestAttachment buildRequestAttachment(FilterableRequestSpecification requestSpec) {
        Prettifier prettifier = new Prettifier();
        Set<String> hiddenHeaders = hiddenHeaders(requestSpec);

        HttpRequestAttachment.Builder builder = create("Request", requestSpec.getURI())
                .setMethod(requestSpec.getMethod())
                .setHeaders(toMap(requestSpec.getHeaders(), hiddenHeaders))
                .setCookies(toMap(requestSpec.getCookies(), new HashSet<>()));

        if (Objects.nonNull(requestSpec.getBody())) {
            builder.setBody(truncate(prettifier.getPrettifiedBodyIfPossible(requestSpec)));
        }
        if (Objects.nonNull(requestSpec.getFormParams())) {
            builder.setFormParams(toStringMap(requestSpec.getFormParams()));
        }

        return builder.build();
    }

    private static void logRequest(ExtentTest test, HttpRequestAttachment request) {
        Markup requestLabel = MarkupHelper.createLabel(
                request.getMethod() + " " + request.getUrl(),
                ExtentColor.BLUE
        );
        test.info(requestLabel);

        test.info(MarkupHelper.createLabel("cURL", ExtentColor.GREY));
        test.info(MarkupHelper.createCodeBlock(request.getCurl()));

        if (!request.getHeaders().isEmpty()) {
            test.info(MarkupHelper.createLabel("Request headers", ExtentColor.GREY));
            test.info(MarkupHelper.createCodeBlock(formatMap(request.getHeaders())));
        }

        String body = request.getBody();
        if (body != null && !body.isBlank()) {
            test.info(MarkupHelper.createLabel("Request body", ExtentColor.GREY));
            test.info(MarkupHelper.createCodeBlock(body, CodeLanguage.JSON));
        }
    }

    private static void logResponse(ExtentTest test,
                                      FilterableRequestSpecification requestSpec,
                                      Response response) {
        Prettifier prettifier = new Prettifier();
        Set<String> hiddenHeaders = hiddenHeaders(requestSpec);
        Map<String, String> responseHeaders = toMap(response.getHeaders(), hiddenHeaders);
        String responseBody = truncate(prettifier.getPrettifiedBodyIfPossible(response, response.getBody()));

        ExtentColor statusColor = response.getStatusCode() >= 400 ? ExtentColor.RED : ExtentColor.GREEN;
        Markup responseLabel = MarkupHelper.createLabel(
                "Response " + response.getStatusCode() + " " + response.getStatusLine(),
                statusColor
        );
        test.info(responseLabel);

        if (!responseHeaders.isEmpty()) {
            test.info(MarkupHelper.createLabel("Response headers", ExtentColor.GREY));
            test.info(MarkupHelper.createCodeBlock(formatMap(responseHeaders)));
        }

        if (responseBody != null && !responseBody.isBlank()) {
            test.info(MarkupHelper.createLabel("Response body", ExtentColor.GREY));
            test.info(MarkupHelper.createCodeBlock(responseBody, CodeLanguage.JSON));
        }
    }

    private static Set<String> hiddenHeaders(FilterableRequestSpecification requestSpec) {
        Set<String> hidden = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        hidden.addAll(Objects.requireNonNull(requestSpec.getConfig().getLogConfig().blacklistedHeaders()));
        return hidden;
    }

    private static Map<String, String> toMap(Iterable<? extends NameAndValue> items, Set<String> toHide) {
        Map<String, String> result = new HashMap<>();
        items.forEach(item -> result.put(
                item.getName(),
                toHide.contains(item.getName()) ? HIDDEN_PLACEHOLDER : item.getValue()
        ));
        return result;
    }

    private static Map<String, String> toStringMap(Map<String, ?> source) {
        Map<String, String> result = new HashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((key, value) -> result.put(key, value == null ? "" : String.valueOf(value)));
        return result;
    }

    private static String formatMap(Map<String, String> map) {
        return map.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static String truncate(String body) {
        if (body == null) {
            return null;
        }
        if (body.length() <= MAX_BODY_CHARS) {
            return body;
        }
        return body.substring(0, MAX_BODY_CHARS)
                + "\n\n... [truncated, total " + body.length() + " chars]";
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 1;
    }
}
