package com.example.privacy_lab.export;

import com.example.privacy_lab.security.AppPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class PersonalDataController {

    private static final Logger log = LoggerFactory.getLogger(PersonalDataController.class);
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final PersonalDataService service;

    public PersonalDataController(PersonalDataService service) {
        this.service = service;
    }

    @GetMapping("/{id}/personal-data")
    public ResponseEntity<PersonalDataExport> export(@PathVariable long id,
            @AuthenticationPrincipal AppPrincipal principal) {
        boolean self = principal.id().equals(id);
        boolean admin = ADMIN_ROLE.equals(principal.role());

        // Порівнюємо автентифікованого суб'єкта з ID, а не покладаємось на path
        // parameter.
        // Для чужого ID (існуючого чи ні) відповідь однакова: 403 без тіла.
        if (!self && !admin) {
            log.warn("SECURITY_EVENT op=PERSONAL_DATA_EXPORT actor={} subject={} result=DENIED",
                    principal.id(), id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<PersonalDataExport> result = service.export(id);
        if (result.isEmpty()) {
            log.info("SECURITY_EVENT op=PERSONAL_DATA_EXPORT actor={} subject={} result=NOT_FOUND",
                    principal.id(), id);
            return ResponseEntity.notFound().build();
        }

        // У лог потрапляє лише факт операції та ID, не вміст експорту.
        log.info("SECURITY_EVENT op=PERSONAL_DATA_EXPORT actor={} subject={} result=OK",
                principal.id(), id);
        return ResponseEntity.ok(result.get());
    }
}