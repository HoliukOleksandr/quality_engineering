package com.example.privacy_lab.consent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, Long> {
    List<ConsentHistory> findByUserIdOrderByOccurredAtAsc(Long userId);
}