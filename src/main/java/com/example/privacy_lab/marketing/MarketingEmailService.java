package com.example.privacy_lab.marketing;

import com.example.privacy_lab.consent.ConsentPolicyGate;
import com.example.privacy_lab.consent.ConsentPurpose;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Залежна дія: маркетингова розсилка. Без дозволу Policy Gate не має жодного
 * побічного ефекту.
 */
@Service
@RequiredArgsConstructor
public class MarketingEmailService {

    private static final Logger log = LoggerFactory.getLogger(MarketingEmailService.class);

    public enum Outcome {
        SENT, DENIED
    }

    private final ConsentPolicyGate gate;
    private final MarketingOutboxRepository outbox;

    @Transactional
    public Outcome sendCampaign(long userId, String subject) {
        if (!gate.isAllowed(userId, ConsentPurpose.MARKETING_EMAIL)) {
            log.info("CONSENT_GATE op=MARKETING_EMAIL user={} result=DENY", userId);
            return Outcome.DENIED;
        }

        MarketingOutbox message = new MarketingOutbox();
        message.setUserId(userId);
        message.setSubject(subject);
        outbox.save(message);

        log.info("CONSENT_GATE op=MARKETING_EMAIL user={} result=ALLOW", userId);
        return Outcome.SENT;
    }
}