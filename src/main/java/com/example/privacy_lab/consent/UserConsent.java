package com.example.privacy_lab.consent;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Поточний стан згоди користувача для однієї мети (один рядок на пару user +
 * purpose).
 */
@Entity
@Table(name = "user_consents", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "purpose" }))
@Getter
@Setter
@NoArgsConstructor
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentPurpose purpose;

    @Column(nullable = false)
    private boolean granted;

    /** Яку версію політики прийняв користувач */
    private String policyVersion;

    private Instant grantedAt;

    private Instant withdrawnAt;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    /** Через який канал дано згоду (наприклад, WEB, API) */
    private String source;
}