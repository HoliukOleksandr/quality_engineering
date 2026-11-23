package com.example.privacy_lab.consent;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Історія змін згоди: записи лише додаються, ніколи не перезаписуються. */
@Entity
@Table(name = "consent_history")
@Getter
@Setter
@NoArgsConstructor
public class ConsentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentPurpose purpose;

    /** GRANT або REVOKE */
    @Column(nullable = false)
    private String action;

    private String policyVersion;

    private String source;

    @Column(nullable = false)
    private Instant occurredAt = Instant.now();
}