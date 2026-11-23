package com.example.privacy_lab.anonymization;

import com.example.privacy_lab.audit.AuditEvent;
import com.example.privacy_lab.audit.AuditEventRepository;
import com.example.privacy_lab.consent.ConsentPurpose;
import com.example.privacy_lab.consent.ConsentService;
import com.example.privacy_lab.domain.ActivityEvent;
import com.example.privacy_lab.domain.AppUser;
import com.example.privacy_lab.domain.CustomerOrder;
import com.example.privacy_lab.repository.ActivityEventRepository;
import com.example.privacy_lab.repository.AppUserRepository;
import com.example.privacy_lab.repository.CustomerOrderRepository;
import com.example.privacy_lab.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Незворотна анонімізація користувача в одній транзакції.
 * Політика: app_users ANONYMIZE, user_settings DELETE, customer_orders RETAIN
 * (без адреси й телефону),
 * activity_events ANONYMIZE (без IP), user_consents RETAIN як історія, але всі
 * згоди відкликаються,
 * audit_events RETAIN (лише числові ID).
 */
@Service
@RequiredArgsConstructor
public class AnonymizationService {

    private static final Logger log = LoggerFactory.getLogger(AnonymizationService.class);

    public enum Outcome {
        ANONYMIZED, ALREADY_ANONYMIZED, NOT_FOUND
    }

    private final AppUserRepository users;
    private final UserSettingsRepository settings;
    private final CustomerOrderRepository orders;
    private final ActivityEventRepository events;
    private final AuditEventRepository audit;
    private final PasswordEncoder encoder;
    private final ConsentService consentService;

    @Transactional
    public Outcome anonymize(long actorId, long subjectId) {
        Optional<AppUser> found = users.findById(subjectId);
        if (found.isEmpty()) {
            writeAudit(actorId, subjectId, Outcome.NOT_FOUND);
            return Outcome.NOT_FOUND;
        }

        AppUser user = found.get();

        // Ідемпотентність: повторний запит нічого не змінює і нічого не відновлює
        if (user.getAnonymizedAt() != null) {
            writeAudit(actorId, subjectId, Outcome.ALREADY_ANONYMIZED);
            return Outcome.ALREADY_ANONYMIZED;
        }

        // app_users: ANONYMIZE. Унікальний email (немає зв'язку з початковим), пароль
        // випадковий.
        user.setEmail("deleted-" + UUID.randomUUID() + "@anon.invalid");
        user.setFullName("Anonymized User");
        user.setPhone(null);
        user.setAddress(null);
        user.setResetToken(null);
        user.setFraudFlag(false);
        user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        user.setRole("ROLE_ANONYMIZED");
        user.setAnonymizedAt(Instant.now());
        users.save(user);

        // user_settings: DELETE
        settings.findByUserId(subjectId).ifPresent(settings::delete);

        // customer_orders: RETAIN (сума, дата, зв'язок), але PII прибираємо
        List<CustomerOrder> userOrders = orders.findByUserId(subjectId);
        userOrders.forEach(o -> {
            o.setShippingAddress(null);
            o.setShippingPhone(null);
        });
        orders.saveAll(userOrders);

        // activity_events: ANONYMIZE (технічний ідентифікатор IP)
        List<ActivityEvent> userEvents = events.findByUserIdOrderByCreatedAtAsc(subjectId);
        userEvents.forEach(e -> e.setIpAddress(null));
        events.saveAll(userEvents);

        // user_consents: RETAIN як доказ історії (там лише числовий ID), але
        // відкликаємо всі згоди,
        // щоб Policy Gate блокував будь-які залежні дії для анонімізованого суб'єкта
        for (ConsentPurpose purpose : ConsentPurpose.values()) {
            consentService.revoke(subjectId, purpose, "ANONYMIZATION");
        }

        writeAudit(actorId, subjectId, Outcome.ANONYMIZED);
        return Outcome.ANONYMIZED;
    }

    /** Лише факт операції та числові ID. Без значень, які були видалені. */
    private void writeAudit(long actorId, long subjectId, Outcome outcome) {
        AuditEvent e = new AuditEvent();
        e.setOperation("ANONYMIZE_USER");
        e.setActorId(actorId);
        e.setSubjectId(subjectId);
        e.setResult(outcome.name());
        e.setCorrelationId(MDC.get("correlationId"));
        audit.save(e);

        log.info("SECURITY_EVENT op=ANONYMIZE_USER actor={} subject={} result={}",
                actorId, subjectId, outcome);
    }
}