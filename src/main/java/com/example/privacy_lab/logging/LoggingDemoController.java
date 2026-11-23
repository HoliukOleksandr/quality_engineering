package com.example.privacy_lab.logging;

import com.example.privacy_lab.domain.AppUser;
import com.example.privacy_lab.repository.AppUserRepository;
import com.example.privacy_lab.security.AppPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Демо для захисту: навмисно логує PII і секрети у чотирьох форматах. */
@RestController
@RequestMapping("/api/demo")
public class LoggingDemoController {

    private static final Logger log = LoggerFactory.getLogger(LoggingDemoController.class);

    private final AppUserRepository users;

    public LoggingDemoController(AppUserRepository users) {
        this.users = users;
    }

    record Credentials(String email, String password) {
    }

    record SignupDto(String phone, Credentials credentials) {
    }

    @GetMapping("/log-pii")
    public Map<String, String> logPii(@AuthenticationPrincipal AppPrincipal principal) {
        AppUser u = users.findById(principal.id()).orElseThrow();
        String fakePassword = "S3cretPass!";
        String fakeToken = "synthetic-access-token-abc123";

        // 1. Звичайний текст
        log.info("Plain text: processing {} phone {} password={} accessToken={}",
                u.getEmail(), u.getPhone(), fakePassword, fakeToken);

        // 2. Структурований JSON
        String json = String.format(
                "{\"op\":\"signup\",\"email\":\"%s\",\"phone\":\"%s\",\"password\":\"%s\",\"accessToken\":\"%s\"}",
                u.getEmail(), u.getPhone(), fakePassword, fakeToken);
        log.info("JSON payload: {}", json);

        // 3. Вкладений DTO (toString містить пароль)
        log.info("Nested DTO: {}", new SignupDto(u.getPhone(), new Credentials(u.getEmail(), fakePassword)));

        // 4. Повідомлення винятку
        try {
            throw new IllegalStateException("User " + u.getEmail() + " phone " + u.getPhone()
                    + " rejected, token=" + fakeToken);
        } catch (IllegalStateException e) {
            log.error("Operation failed", e);
        }

        // Security-подія: лише факт, actor ID, результат (correlation ID додає layout)
        log.info("SECURITY_EVENT op=PII_LOG_DEMO actor={} result=OK", principal.id());

        return Map.of("status", "logged, check server console");
    }
}