package com.example.privacy_lab.marketing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Спостережуваний побічний ефект розсилки: один рядок = одне «надіслане»
 * повідомлення.
 */
@Entity
@Table(name = "marketing_outbox")
@Getter
@Setter
@NoArgsConstructor
public class MarketingOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}