package com.example.privacy_lab.marketing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingOutboxRepository extends JpaRepository<MarketingOutbox, Long> {
    long countByUserId(Long userId);
}