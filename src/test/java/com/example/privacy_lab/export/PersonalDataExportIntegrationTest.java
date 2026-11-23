package com.example.privacy_lab.export;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.privacy_lab.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PersonalDataExportIntegrationTest {

    // Усі дані СИНТЕТИЧНІ (див. DataSeeder)
    private static final String PASSWORD = "Passw0rd!";
    private static final String ALICE = "alice@example.test";
    private static final String BOB = "bob@example.test";
    private static final String ADMIN = "admin@example.test";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AppUserRepository users;

    private final HttpClient http = HttpClient.newHttpClient();

    private long idOf(String email) {
        return users.findByEmail(email).orElseThrow().getId();
    }

    private HttpResponse<String> get(String path, String email) throws Exception {
        HttpRequest.Builder request = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (email != null) {
            String credentials = Base64.getEncoder()
                    .encodeToString((email + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + credentials);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            n++;
        }
        return n;
    }

    @Test
    void ownExport_containsEverySourceAndNoOtherUsersData() throws Exception {
        HttpResponse<String> r = get("/api/users/" + idOf(ALICE) + "/personal-data", ALICE);
        String body = r.body();

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(body)
                .contains("\"schemaVersion\":\"1.0\"")
                .contains("\"timeZone\":\"UTC\"")
                .contains("Alice Testova") // profile
                .contains(ALICE)
                .contains("\"language\":\"uk\"") // settings
                .contains("VIEW_ORDERS") // activity
                .contains("192.0.2.10")
                .contains("\"consents\":[]"); // documented empty section
        assertThat(count(body, "\"orderId\"")).isEqualTo(2); // orders: рівно 2 замовлення Alice
        assertThat(body).contains("150.00").contains("89.90");

        // Жодних даних Bob
        assertThat(body)
                .doesNotContain("Bob Sample")
                .doesNotContain(BOB)
                .doesNotContain("42.50")
                .doesNotContain("192.0.2.20");
    }

    @Test
    void export_neverContainsSecurityFields() throws Exception {
        String body = get("/api/users/" + idOf(ALICE) + "/personal-data", ALICE).body();

        assertThat(body)
                .doesNotContain("passwordHash")
                .doesNotContain("password")
                .doesNotContain("$2a$")
                .doesNotContain("resetToken")
                .doesNotContain("synthetic-reset-token-alice-123")
                .doesNotContain("fraudFlag")
                .doesNotContain("ROLE_");
    }

    @Test
    void otherUser_isDeniedWithEmptyBody() throws Exception {
        HttpResponse<String> r = get("/api/users/" + idOf(ALICE) + "/personal-data", BOB);

        assertThat(r.statusCode()).isEqualTo(403);
        assertThat(r.body()).isEmpty();
    }

    @Test
    void nonexistentSubject_asOrdinaryUser_looksIdenticalToOtherUser() throws Exception {
        HttpResponse<String> existingOther = get("/api/users/" + idOf(ALICE) + "/personal-data", BOB);
        HttpResponse<String> missing = get("/api/users/999999/personal-data", BOB);

        // Не розкриваємо, чи існує чужий профіль
        assertThat(missing.statusCode()).isEqualTo(existingOther.statusCode()).isEqualTo(403);
        assertThat(missing.body()).isEmpty();
    }

    @Test
    void nonexistentSubject_asAdmin_returns404() throws Exception {
        HttpResponse<String> r = get("/api/users/999999/personal-data", ADMIN);

        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.body()).isEmpty();
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        HttpResponse<String> r = get("/api/users/" + idOf(ALICE) + "/personal-data", null);

        assertThat(r.statusCode()).isEqualTo(401);
    }

    @Test
    void admin_withExplicitRole_canExport() throws Exception {
        HttpResponse<String> r = get("/api/users/" + idOf(ALICE) + "/personal-data", ADMIN);

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("Alice Testova");
    }

    @Test
    void export_doesNotLeakPersonalDataIntoLogs() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger("ROOT");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.setContext(root.getLoggerContext());
        captured.start();
        root.addAppender(captured);
        try {
            HttpResponse<String> r = get("/api/users/" + idOf(ALICE) + "/personal-data", ALICE);
            assertThat(r.statusCode()).isEqualTo(200);
        } finally {
            root.detachAppender(captured);
        }

        List<String> messages = captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(m -> m.contains("op=PERSONAL_DATA_EXPORT") && m.contains("result=OK"));
        assertThat(String.join("\n", messages))
                .doesNotContain(ALICE)
                .doesNotContain("Alice Testova")
                .doesNotContain("+380000000001")
                .doesNotContain("1 Test Street")
                .doesNotContain("schemaVersion");
    }
}