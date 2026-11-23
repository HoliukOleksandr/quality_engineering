package com.example.privacy_lab.consent;

import java.util.Objects;

/** Незмінний Value Object ключа згоди користувача. */
public record ConsentKey(long userId, ConsentPurpose purpose) {
    public ConsentKey {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(purpose, "purpose must not be null");
    }
}