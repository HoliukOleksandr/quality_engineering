package com.example.privacy_lab.consent;

import com.example.privacy_lab.domain.AppUser;
import com.example.privacy_lab.marketing.MarketingOutboxRepository;
import com.example.privacy_lab.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsentPolicyIntegrationTest {

    // Усі дані СИНТЕТИЧНІ
    private static final String PASSWORD = "Passw0rd!";
    private static final String ADMIN = "admin@example.test";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AppUserRepository users;
    @Autowired
    private UserConsentRepository consents;
    @Autowired
    private ConsentHistoryRepository history;
    @Autowired
    private MarketingOutboxRepository outbox;
    @Autowired
    private ConsentPolicyGate gate;
    @Autowired
    private PasswordEncoder encoder;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Subject(long id, String email) {
    }

    private Subject createSubject() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        AppUser u = new AppUser();
        u.setEmail("consent-" + tag + "@example.test");
        u.setFullName("Consent Fixture " + tag);
        u.setPhone("+380990000000");
        u.setAddress("5 Consent Road, Testville");
        u.setPasswordHash(encoder.encode(PASSWORD));
        u = users.save(u);
        return new Subject(u.getId(), u.getEmail());
    }

    private HttpResponse<String> call(String method, String path, String email) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if ("POST".equals(method)) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.GET();
        }
        String credentials = Base64.getEncoder()
                .encodeToString((email + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
        request.header("Authorization", "Basic " + credentials);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> grant(long id, String purpose, String actorEmail) throws Exception {
        return call("POST", "/api/users/" + id + "/consents/" + purpose + "/grant", actorEmail);
    }

    private HttpResponse<String> revoke(long id, String purpose, String actorEmail) throws Exception {
        return call("POST", "/api/users/" + id + "/consents/" + purpose + "/revoke", actorEmail);
    }

    private HttpResponse<String> sendMarketing(long id) throws Exception {
        return call("POST", "/api/users/" + id + "/marketing/send", ADMIN);
    }

    @Test
    void grantAllowRevokeDeny_fullScenario() throws Exception {
        Subject s = createSubject();

        // 1. Початковий стан: DENY, побічного ефекту немає
        HttpResponse<String> initial = sendMarketing(s.id());
        assertThat(initial.statusCode()).isEqualTo(409);
        assertThat(initial.body()).contains("DENIED");
        assertThat(outbox.countByUserId(s.id())).isZero();

        // 2. GRANT для конкретної мети
        HttpResponse<String> granted = grant(s.id(), "MARKETING_EMAIL", s.email());
        assertThat(granted.statusCode()).isEqualTo(200);
        assertThat(granted.body()).contains("GRANTED");

        // Доказові метадані: версія політики, канал, час
        UserConsent c = consents.findByUserIdAndPurpose(s.id(), ConsentPurpose.MARKETING_EMAIL).orElseThrow();
        assertThat(c.isGranted()).isTrue();
        assertThat(c.getPolicyVersion()).isEqualTo("v1");
        assertThat(c.getSource()).isEqualTo("API");
        assertThat(c.getGrantedAt()).isNotNull();
        assertThat(c.getWithdrawnAt()).isNull();

        // 3. ALLOW: дія виконана, побічний ефект зафіксований
        HttpResponse<String> sent = sendMarketing(s.id());
        assertThat(sent.statusCode()).isEqualTo(200);
        assertThat(sent.body()).contains("SENT");
        assertThat(outbox.countByUserId(s.id())).isEqualTo(1);

        // 4. REVOKE тієї самої згоди
        HttpResponse<String> revoked = revoke(s.id(), "MARKETING_EMAIL", s.email());
        assertThat(revoked.statusCode()).isEqualTo(200);
        assertThat(revoked.body()).contains("REVOKED");

        c = consents.findByUserIdAndPurpose(s.id(), ConsentPurpose.MARKETING_EMAIL).orElseThrow();
        assertThat(c.isGranted()).isFalse();
        assertThat(c.getWithdrawnAt()).isNotNull();

        // 5. DENY одразу після відкликання: нового побічного ефекту немає
        HttpResponse<String> afterRevoke = sendMarketing(s.id());
        assertThat(afterRevoke.statusCode()).isEqualTo(409);
        assertThat(outbox.countByUserId(s.id())).isEqualTo(1);

        // Історія зберігає обидві зміни в правильному порядку
        List<String> actions = history.findByUserIdOrderByOccurredAtAsc(s.id()).stream()
                .map(ConsentHistory::getAction).toList();
        assertThat(actions).containsExactly("GRANT", "REVOKE");
    }

    @Test
    void consentForOnePurpose_doesNotAllowAnother() throws Exception {
        Subject s = createSubject();

        assertThat(grant(s.id(), "OPTIONAL_ANALYTICS", s.email()).statusCode()).isEqualTo(200);

        assertThat(gate.isAllowed(s.id(), ConsentPurpose.OPTIONAL_ANALYTICS)).isTrue();
        assertThat(gate.isAllowed(s.id(), ConsentPurpose.MARKETING_EMAIL)).isFalse();
        assertThat(sendMarketing(s.id()).statusCode()).isEqualTo(409);
        assertThat(outbox.countByUserId(s.id())).isZero();
    }

    @Test
    void grantAndRevoke_areIdempotent_andKeepSingleConsistentRecord() throws Exception {
        Subject s = createSubject();

        assertThat(grant(s.id(), "MARKETING_EMAIL", s.email()).body()).contains("\"GRANTED\"");
        assertThat(grant(s.id(), "MARKETING_EMAIL", s.email()).body()).contains("ALREADY_GRANTED");
        assertThat(history.findByUserIdOrderByOccurredAtAsc(s.id())).hasSize(1);

        assertThat(revoke(s.id(), "MARKETING_EMAIL", s.email()).body()).contains("\"REVOKED\"");
        assertThat(revoke(s.id(), "MARKETING_EMAIL", s.email()).body()).contains("ALREADY_REVOKED");
        assertThat(history.findByUserIdOrderByOccurredAtAsc(s.id())).hasSize(2);

        // Один рядок на пару user + purpose, без суперечливих дублікатів
        assertThat(consents.findByUserId(s.id())).hasSize(1);
        assertThat(consents.findByUserId(s.id()).get(0).isGranted()).isFalse();
    }

    @Test
    void revokeWithoutPriorGrant_createsNoRecords() throws Exception {
        Subject s = createSubject();

        HttpResponse<String> r = revoke(s.id(), "MARKETING_EMAIL", s.email());

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("ALREADY_REVOKED");
        assertThat(consents.findByUserId(s.id())).isEmpty();
        assertThat(history.findByUserIdOrderByOccurredAtAsc(s.id())).isEmpty();
    }

    @Test
    void outdatedPolicyVersion_isDenied_untilConsentIsGivenAgain() throws Exception {
        Subject s = createSubject();
        grant(s.id(), "MARKETING_EMAIL", s.email());

        UserConsent c = consents.findByUserIdAndPurpose(s.id(), ConsentPurpose.MARKETING_EMAIL).orElseThrow();
        c.setPolicyVersion("v0");
        consents.save(c);

        assertThat(gate.isAllowed(s.id(), ConsentPurpose.MARKETING_EMAIL)).isFalse();
        assertThat(sendMarketing(s.id()).statusCode()).isEqualTo(409);
        assertThat(outbox.countByUserId(s.id())).isZero();

        // Повторна згода вже на актуальну версію політики знову відкриває дію
        assertThat(grant(s.id(), "MARKETING_EMAIL", s.email()).body()).contains("\"GRANTED\"");
        assertThat(gate.isAllowed(s.id(), ConsentPurpose.MARKETING_EMAIL)).isTrue();
    }

    @Test
    void onlyTheUserCanChangeOwnConsent_andUnknownPurposeIsRejected() throws Exception {
        Subject owner = createSubject();
        Subject stranger = createSubject();

        HttpResponse<String> byStranger = grant(owner.id(), "MARKETING_EMAIL", stranger.email());
        assertThat(byStranger.statusCode()).isEqualTo(403);
        assertThat(byStranger.body()).isEmpty();

        HttpResponse<String> byAdmin = grant(owner.id(), "MARKETING_EMAIL", ADMIN);
        assertThat(byAdmin.statusCode()).isEqualTo(403);

        HttpResponse<String> unknown = grant(owner.id(), "NEWSLETTER", owner.email());
        assertThat(unknown.statusCode()).isEqualTo(400);

        assertThat(consents.findByUserId(owner.id())).isEmpty();
        assertThat(history.findByUserIdOrderByOccurredAtAsc(owner.id())).isEmpty();
    }

    @Test
    void withoutMarketingConsent_systemFunctionsStillWork() throws Exception {
        Subject s = createSubject();

        // Згода нічого не блокує поза optional-операціями: право доступу працює без
        // жодної згоди
        HttpResponse<String> export = call("GET", "/api/users/" + s.id() + "/personal-data", s.email());

        assertThat(export.statusCode()).isEqualTo(200);
        assertThat(export.body()).contains("\"schemaVersion\":\"1.0\"");
    }
}