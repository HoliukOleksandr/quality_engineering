package com.example.privacy_lab.export;

import com.example.privacy_lab.consent.UserConsent;
import com.example.privacy_lab.consent.UserConsentRepository;
import com.example.privacy_lab.domain.AppUser;
import com.example.privacy_lab.domain.CustomerOrder;
import com.example.privacy_lab.repository.ActivityEventRepository;
import com.example.privacy_lab.repository.AppUserRepository;
import com.example.privacy_lab.repository.CustomerOrderRepository;
import com.example.privacy_lab.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Збирає дані ОДНОГО суб'єкта з усіх джерел. Свідомо НЕ експортуються:
 * passwordHash, resetToken, fraudFlag, role та будь-які дані інших
 * користувачів.
 */
@Service
@RequiredArgsConstructor
public class PersonalDataService {

        static final String SCHEMA_VERSION = "1.0";

        private final AppUserRepository users;
        private final UserSettingsRepository settings;
        private final CustomerOrderRepository orders;
        private final ActivityEventRepository events;
        private final UserConsentRepository consentRepository;

        @Transactional(readOnly = true)
        public Optional<PersonalDataExport> export(long userId) {
                return users.findById(userId).map(this::build);
        }

        private PersonalDataExport build(AppUser u) {
                Long id = u.getId();

                var profile = new PersonalDataExport.Profile(
                                u.getFullName(), u.getEmail(), u.getPhone(), u.getAddress(),
                                u.getCreatedAt().toString());

                // Відсутня категорія -> передбачувана порожня структура, а не пропуск
                var userSettings = settings.findByUserId(id)
                                .map(s -> new PersonalDataExport.Settings(s.getLanguage(), s.getTheme(),
                                                s.getTimeZone()))
                                .orElse(new PersonalDataExport.Settings(null, null, null));

                List<PersonalDataExport.OrderRecord> orderList = orders.findByUserId(id).stream()
                                .sorted(Comparator.comparing(CustomerOrder::getId))
                                .map(o -> new PersonalDataExport.OrderRecord(
                                                o.getId(),
                                                o.getCreatedAt().toString(),
                                                o.getAmount() == null ? null : o.getAmount().toPlainString(),
                                                o.getShippingAddress(),
                                                o.getShippingPhone()))
                                .toList();

                List<PersonalDataExport.ActivityRecord> activity = events.findByUserIdOrderByCreatedAtAsc(id).stream()
                                .map(e -> new PersonalDataExport.ActivityRecord(
                                                e.getAction(), e.getIpAddress(), e.getCreatedAt().toString()))
                                .toList();

                List<PersonalDataExport.ConsentRecord> consents = consentRepository.findByUserId(id).stream()
                                .sorted(Comparator.comparing((UserConsent c) -> c.getPurpose().name()))
                                .map(c -> new PersonalDataExport.ConsentRecord(
                                                c.getPurpose().name(),
                                                c.isGranted() ? "GRANTED" : "REVOKED",
                                                c.getPolicyVersion(),
                                                c.getGrantedAt() == null ? null : c.getGrantedAt().toString(),
                                                c.getWithdrawnAt() == null ? null : c.getWithdrawnAt().toString(),
                                                c.getSource()))
                                .toList();

                Map<String, String> sources = new LinkedHashMap<>();
                sources.put("profile", "app_users");
                sources.put("settings", "user_settings");
                sources.put("orders", "customer_orders");
                sources.put("activity", "activity_events");
                sources.put("consents", "user_consents");

                var meta = new PersonalDataExport.Meta(
                                SCHEMA_VERSION,
                                Instant.now().toString(),
                                "UTC",
                                id,
                                List.of("profile", "settings", "orders", "activity", "consents"),
                                sources);

                return new PersonalDataExport(meta, profile, userSettings, orderList, activity, consents);
        }
}