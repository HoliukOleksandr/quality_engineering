package com.example.privacy_lab.consent;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Реєстрація та відкликання згод. Кожна зміна пишеться в історію; повтор нічого
 * не ламає.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);

    public enum Result {
        GRANTED, ALREADY_GRANTED, REVOKED, ALREADY_REVOKED
    }

    private final UserConsentRepository consents;
    private final ConsentHistoryRepository history;

    @Transactional
    public Result grant(long userId, ConsentPurpose purpose, String source) {
        UserConsent c = consents.findByUserIdAndPurpose(userId, purpose).orElseGet(() -> {
            UserConsent fresh = new UserConsent();
            fresh.setUserId(userId);
            fresh.setPurpose(purpose);
            return fresh;
        });

        // Ідемпотентність: та сама згода тієї самої версії політики вдруге нічого не
        // змінює
        if (c.isGranted() && ConsentPolicyGate.CURRENT_POLICY_VERSION.equals(c.getPolicyVersion())) {
            log.info("CONSENT op=GRANT user={} purpose={} result=ALREADY_GRANTED", userId, purpose);
            return Result.ALREADY_GRANTED;
        }

        Instant now = Instant.now();
        c.setGranted(true);
        c.setPolicyVersion(ConsentPolicyGate.CURRENT_POLICY_VERSION);
        c.setGrantedAt(now);
        c.setWithdrawnAt(null);
        c.setUpdatedAt(now);
        c.setSource(source);
        consents.save(c);

        record(userId, purpose, "GRANT", source);
        log.info("CONSENT op=GRANT user={} purpose={} result=GRANTED", userId, purpose);
        return Result.GRANTED;
    }

    @Transactional
    public Result revoke(long userId, ConsentPurpose purpose, String source) {
        Optional<UserConsent> existing = consents.findByUserIdAndPurpose(userId, purpose);

        // Нічого відкликати: не створюємо жодних суперечливих записів
        if (existing.isEmpty() || !existing.get().isGranted()) {
            log.info("CONSENT op=REVOKE user={} purpose={} result=ALREADY_REVOKED", userId, purpose);
            return Result.ALREADY_REVOKED;
        }

        Instant now = Instant.now();
        UserConsent c = existing.get();
        c.setGranted(false);
        c.setGrantedAt(null); // симуляція помилкової мутації стану (FAIL)
        c.setWithdrawnAt(now);
        c.setUpdatedAt(now);
        c.setSource(source);
        consents.save(c);

        record(userId, purpose, "REVOKE", source);
        log.info("CONSENT op=REVOKE user={} purpose={} result=REVOKED", userId, purpose);
        return Result.REVOKED;
    }

    private void record(long userId, ConsentPurpose purpose, String action, String source) {
        ConsentHistory h = new ConsentHistory();
        h.setUserId(userId);
        h.setPurpose(purpose);
        h.setAction(action);
        h.setPolicyVersion(ConsentPolicyGate.CURRENT_POLICY_VERSION);
        h.setSource(source);
        history.save(h);
    }
}