package com.example.privacy_lab.consent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {
    Optional<UserConsent> findByUserIdAndPurpose(Long userId, ConsentPurpose purpose);

    List<UserConsent> findByUserId(Long userId);
}