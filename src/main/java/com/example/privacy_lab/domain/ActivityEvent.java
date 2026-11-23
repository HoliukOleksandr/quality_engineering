package com.example.privacy_lab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "activity_events")
@Getter
@Setter
@NoArgsConstructor
public class ActivityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private String action;

    /** Технічний ідентифікатор: IP-адреса */
    private String ipAddress;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}