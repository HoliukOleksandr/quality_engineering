package com.example.privacy_lab.consent;

import com.example.privacy_lab.security.AppPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Керування згодами: лише сам користувач може дати або відкликати власну згоду.
 */
@RestController
@RequestMapping("/api/users/{id}/consents")
public class ConsentController {

    private static final Logger log = LoggerFactory.getLogger(ConsentController.class);
    private static final String SOURCE = "API";

    private final ConsentService service;
    private final UserConsentRepository consents;

    public ConsentController(ConsentService service, UserConsentRepository consents) {
        this.service = service;
        this.consents = consents;
    }

    public record ConsentView(String purpose, boolean granted, String policyVersion,
            String grantedAt, String withdrawnAt, String source) {
    }

    @GetMapping
    public ResponseEntity<List<ConsentView>> list(@PathVariable long id,
            @AuthenticationPrincipal AppPrincipal principal) {
        if (!principal.id().equals(id)) {
            return denied(principal.id(), id, "LIST");
        }
        List<ConsentView> views = consents.findByUserId(id).stream()
                .map(c -> new ConsentView(
                        c.getPurpose().name(),
                        c.isGranted(),
                        c.getPolicyVersion(),
                        c.getGrantedAt() == null ? null : c.getGrantedAt().toString(),
                        c.getWithdrawnAt() == null ? null : c.getWithdrawnAt().toString(),
                        c.getSource()))
                .toList();
        return ResponseEntity.ok(views);
    }

    @PostMapping("/{purpose}/grant")
    public ResponseEntity<Map<String, String>> grant(@PathVariable long id,
            @PathVariable String purpose,
            @AuthenticationPrincipal AppPrincipal principal) {
        if (!principal.id().equals(id)) {
            return denied(principal.id(), id, "GRANT");
        }
        Optional<ConsentPurpose> parsed = parse(purpose);
        if (parsed.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "UNKNOWN_PURPOSE"));
        }
        ConsentService.Result result = service.grant(id, parsed.get(), SOURCE);
        return ResponseEntity.ok(Map.of("status", result.name()));
    }

    @PostMapping("/{purpose}/revoke")
    public ResponseEntity<Map<String, String>> revoke(@PathVariable long id,
            @PathVariable String purpose,
            @AuthenticationPrincipal AppPrincipal principal) {
        if (!principal.id().equals(id)) {
            return denied(principal.id(), id, "REVOKE");
        }
        Optional<ConsentPurpose> parsed = parse(purpose);
        if (parsed.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "UNKNOWN_PURPOSE"));
        }
        ConsentService.Result result = service.revoke(id, parsed.get(), SOURCE);
        return ResponseEntity.ok(Map.of("status", result.name()));
    }

    /** Валідація типу згоди: невідомий purpose не створює жодних записів. */
    private static Optional<ConsentPurpose> parse(String raw) {
        try {
            return Optional.of(ConsentPurpose.valueOf(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static <T> ResponseEntity<T> denied(long actor, long subject, String op) {
        log.warn("CONSENT op={} actor={} subject={} result=DENIED", op, actor, subject);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}