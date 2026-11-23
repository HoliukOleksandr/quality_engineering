package com.example.privacy_lab.anonymization;

import com.example.privacy_lab.security.AppPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class AnonymizationController {

    private static final Logger log = LoggerFactory.getLogger(AnonymizationController.class);
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final AnonymizationService service;

    public AnonymizationController(AnonymizationService service) {
        this.service = service;
    }

    /** Лише POST: простий GET-запит ніколи не змінює дані. */
    @PostMapping("/{id}/anonymize")
    public ResponseEntity<Map<String, String>> anonymize(@PathVariable long id,
            @AuthenticationPrincipal AppPrincipal principal) {
        boolean self = principal.id().equals(id);
        boolean admin = ADMIN_ROLE.equals(principal.role());

        // Дозволено лише самому користувачу або адміну. Для чужого ID: 403 без тіла.
        if (!self && !admin) {
            log.warn("SECURITY_EVENT op=ANONYMIZE_USER actor={} subject={} result=DENIED",
                    principal.id(), id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        AnonymizationService.Outcome outcome = service.anonymize(principal.id(), id);
        if (outcome == AnonymizationService.Outcome.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("status", outcome.name()));
    }
}