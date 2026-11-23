package com.example.privacy_lab.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Privacy-safe аудит: лише факт операції, ID актора і суб'єкта, результат і
 * correlation ID.
 * Жодних імен, email, телефонів чи видалених значень.
 * Зв'язку (FK) з app_users немає навмисно: запис лишається після анонімізації.
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String operation;

    private Long actorId;

    private Long subjectId;

    @Column(nullable = false)
    private String result;

    private String correlationId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}