package com.example.privacy_lab.export;

import java.util.List;
import java.util.Map;

/**
 * Контракт експорту персональних даних (schemaVersion 1.0). Усі часові мітки:
 * ISO-8601, UTC.
 */
public record PersonalDataExport(
        Meta meta,
        Profile profile,
        Settings settings,
        List<OrderRecord> orders,
        List<ActivityRecord> activity,
        List<ConsentRecord> consents) {

    public record Meta(
            String schemaVersion,
            String generatedAt,
            String timeZone,
            long subjectId,
            List<String> categories,
            Map<String, String> sources) {
    }

    public record Profile(String fullName, String email, String phone, String address, String registeredAt) {
    }

    public record Settings(String language, String theme, String timeZone) {
    }

    public record OrderRecord(long orderId, String createdAt, String amount,
            String shippingAddress, String shippingPhone) {
    }

    public record ActivityRecord(String action, String ipAddress, String at) {
    }

    public record ConsentRecord(String purpose, String status, String policyVersion,
            String grantedAt, String withdrawnAt, String source) {
    }
}