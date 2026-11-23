package com.example.privacy_lab.consent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Policy Gate: вирішує ALLOW/DENY прямо перед залежною дією.
 * Читає актуальний стан із БД при кожному виклику (без кешу), тому відкликання
 * діє негайно.
 * DENY, якщо згоди немає, вона відкликана або дана для іншої мети чи версії
 * політики.
 */
@Component
@RequiredArgsConstructor
public class ConsentPolicyGate {

    public static final String CURRENT_POLICY_VERSION = "v1";

    private final UserConsentRepository consents;

    public boolean isAllowed(long userId, ConsentPurpose purpose) {
        return consents.findByUserIdAndPurpose(userId, purpose)
                .filter(UserConsent::isGranted)
                .filter(c -> CURRENT_POLICY_VERSION.equals(c.getPolicyVersion()))
                .isPresent();
    }
}