package com.automation.api.util;

import io.qameta.allure.Allure;

import java.nio.charset.StandardCharsets;

/**
 * Adds structured attachments to Allure reports (test classpath only).
 */
public final class AllureAttachmentUtils {

    private AllureAttachmentUtils() {
    }

    public static void attachJson(String name, String json) {
        if (json == null) {
            return;
        }
        Allure.addAttachment(name, "application/json", json, ".json");
    }

    public static void attachText(String name, String body) {
        if (body == null) {
            return;
        }
        Allure.addAttachment(name, "text/plain", body, ".txt");
    }

    public static void attachBytes(String name, String mimeType, byte[] data) {
        if (data == null) {
            return;
        }
        Allure.addAttachment(name, mimeType, new String(data, StandardCharsets.UTF_8));
    }
}
