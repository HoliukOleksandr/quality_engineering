package com.example.privacy_lab.repository;

import com.example.privacy_lab.domain.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {
    List<ActivityEvent> findByUserIdOrderByCreatedAtAsc(Long userId);
}