package com.example.privacy_lab.marketing;

import com.example.privacy_lab.security.AppPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Запуск розсилки для користувача. Лише адміністратор. Результат визначає
 * Policy Gate.
 */
@RestController
@RequestMapping("/api/users/{id}/marketing")
public class MarketingController {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final MarketingEmailService service;

    public MarketingController(MarketingEmailService service) {
        this.service = service;
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> send(@PathVariable long id,
            @AuthenticationPrincipal AppPrincipal principal) {
        if (!ADMIN_ROLE.equals(principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        MarketingEmailService.Outcome outcome = service.sendCampaign(id, "Autumn campaign");
        if (outcome == MarketingEmailService.Outcome.SENT) {
            return ResponseEntity.ok(Map.of("status", "SENT"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "DENIED"));
    }
}