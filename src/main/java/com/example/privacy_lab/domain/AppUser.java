package com.example.privacy_lab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String fullName;

    private String phone;

    private String address;

    /** Security-sensitive: ніколи не потрапляє в експорт і в логи */
    @Column(nullable = false)
    private String passwordHash;

    /** Security-sensitive: ніколи не потрапляє в експорт і в логи */
    private String resetToken;

    /** Внутрішній antifraud-прапорець: не експортується */
    private boolean fraudFlag;

    @Column(nullable = false)
    private String role = "ROLE_USER";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant anonymizedAt;
}