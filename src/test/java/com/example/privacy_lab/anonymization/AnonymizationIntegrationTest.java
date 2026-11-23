package com.example.privacy_lab.anonymization;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.privacy_lab.audit.AuditEvent;
import com.example.privacy_lab.audit.AuditEventRepository;
import com.example.privacy_lab.domain.ActivityEvent;
import com.example.privacy_lab.domain.AppUser;
import com.example.privacy_lab.domain.CustomerOrder;
import com.example.privacy_lab.domain.UserSettings;
import com.example.privacy_lab.repository.ActivityEventRepository;
import com.example.privacy_lab.repository.AppUserRepository;
import com.example.privacy_lab.repository.CustomerOrderRepository;
import com.example.privacy_lab.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
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
class AnonymizationIntegrationTest {

    // Усі дані СИНТЕТИЧНІ
    private static final String PASSWORD = "Passw0rd!";
    private static final String BOB = "bob@example.test";
    private static final String ADMIN = "admin@example.test";
    private static final String IP = "192.0.2.77";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AppUserRepository users;
    @Autowired
    private UserSettingsRepository settings;
    @Autowired
    private CustomerOrderRepository orders;
    @Autowired
    private ActivityEventRepository events;
    @Autowired
    private AuditEventRepository audit;
    @Autowired
    private PasswordEncoder encoder;

    private final HttpClient http = HttpClient.newHttpClient();

    /** Початкові PII піддослідного користувача. */
    private record Fixture(long id, String email, String name, String phone, String address, String resetToken) {
        List<String> pii() {
            return List.of(email, name, phone, address, resetToken, IP);
        }
    }

    private Fixture createVictim() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        AppUser u = new AppUser();
        u.setEmail("victim-" + tag + "@example.test");
        u.setFullName("Victor Fixture " + tag);
        u.setPhone("+380991112233");
        u.setAddress("77 Fixture Lane, Testville");
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setResetToken("synthetic-reset-token-" + tag);
        u.setFraudFlag(true);
        u = users.save(u);

        UserSettings s = new UserSettings();
        s.setUser(u);
        s.setLanguage("uk");
        s.setTheme("dark");
        s.setTimeZone("Europe/Kyiv");
        settings.save(s);

        for (String amount : List.of("10.00", "20.50")) {
            CustomerOrder o = new CustomerOrder();
            o.setUser(u);
            o.setShippingAddress("77 Fixture Lane, Testville");
            o.setShippingPhone("+380991112233");
            o.setAmount(new BigDecimal(amount));
            orders.save(o);
        }

        for (String action : List.of("LOGIN", "VIEW_ORDERS")) {
            ActivityEvent e = new ActivityEvent();
            e.setUser(u);
            e.setAction(action);
            e.setIpAddress(IP);
            events.save(e);
        }

        return new Fixture(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getAddress(),
                u.getResetToken());
    }

    /**
     * Збирає в один рядок усе, що зберігається про користувача в усіх охоплених
     * джерелах.
     */
    private String dumpAllSources(long userId) {
        StringBuilder sb = new StringBuilder();
        users.findById(userId).ifPresent(u -> sb
                .append(u.getEmail()).append('|').append(u.getFullName()).append('|')
                .append(u.getPhone()).append('|').append(u.getAddress()).append('|')
                .append(u.getResetToken()));
        settings.findByUserId(userId).ifPresent(s -> sb.append("|SETTINGS:").append(s.getLanguage()));
        orders.findByUserId(userId).forEach(o -> sb
                .append('|').append(o.getShippingAddress()).append('|').append(o.getShippingPhone()));
        events.findByUserIdOrderByCreatedAtAsc(userId).forEach(e -> sb.append('|').append(e.getIpAddress()));
        audit.findBySubjectIdOrderByCreatedAtAsc(userId).forEach(a -> sb
                .append('|').append(a.getOperation()).append('|').append(a.getResult())
                .append('|').append(a.getCorrelationId()));
        return sb.toString();
    }

    private void assertNoPii(String text, Fixture v) {
        for (String value : v.pii()) {
            assertThat(text).doesNotContain(value);
        }
    }

    private HttpResponse<String> send(String method, String path, String email) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if ("POST".equals(method)) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.GET();
        }
        if (email != null) {
            String credentials = Base64.getEncoder()
                    .encodeToString((email + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + credentials);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> anonymize(long subjectId, String actorEmail) throws Exception {
        return send("POST", "/api/users/" + subjectId + "/anonymize", actorEmail);
    }

    @Test
    void anonymize_removesPiiEverywhere_keepsBusinessRecords_andLogsNothingPersonal() throws Exception {
        Fixture v = createVictim();

        // ДО: початкові PII справді є в усіх джерелах (перевірка вміє виявляти PII)
        String before = dumpAllSources(v.id());
        for (String value : v.pii()) {
            assertThat(before).contains(value);
        }
        assertThat(before).contains("SETTINGS:");

        Logger root = (Logger) LoggerFactory.getLogger("ROOT");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.setContext(root.getLoggerContext());
        captured.start();
        root.addAppender(captured);
        HttpResponse<String> r;
        try {
            r = anonymize(v.id(), v.email());
        } finally {
            root.detachAppender(captured);
        }

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("ANONYMIZED");

        // ПІСЛЯ: жодного початкового PII в жодному джерелі
        assertNoPii(dumpAllSources(v.id()), v);

        // Бізнес-записи збережені, зв'язки цілі (немає orphan rows)
        AppUser user = users.findById(v.id()).orElseThrow();
        assertThat(user.getAnonymizedAt()).isNotNull();
        assertThat(user.getEmail()).endsWith("@anon.invalid");
        assertThat(user.getResetToken()).isNull();
        assertThat(settings.findByUserId(v.id())).isEmpty();

        List<CustomerOrder> keptOrders = orders.findByUserId(v.id());
        assertThat(keptOrders).hasSize(2);
        List<String> amounts = keptOrders.stream().map(o -> o.getAmount().toPlainString()).toList();
        assertThat(amounts).containsExactlyInAnyOrder("10.00", "20.50");
        assertThat(events.findByUserIdOrderByCreatedAtAsc(v.id())).hasSize(2);

        // Audit: лише факт, актор, результат, без PII
        List<AuditEvent> trail = audit.findBySubjectIdOrderByCreatedAtAsc(v.id());
        assertThat(trail).hasSize(1);
        assertThat(trail.get(0).getOperation()).isEqualTo("ANONYMIZE_USER");
        assertThat(trail.get(0).getResult()).isEqualTo("ANONYMIZED");
        assertThat(trail.get(0).getActorId()).isEqualTo(v.id());

        // Логи: є факт операції, немає значень, які видаляли
        List<String> messages = captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(m -> m.contains("op=ANONYMIZE_USER") && m.contains("result=ANONYMIZED"));
        assertNoPii(String.join("\n", messages), v);

        // Старі облікові дані більше не працюють
        assertThat(anonymize(v.id(), v.email()).statusCode()).isEqualTo(401);
    }

    @Test
    void repeatedRequest_isIdempotent_andDoesNotRestoreOrCreateAnything() throws Exception {
        Fixture v = createVictim();
        assertThat(anonymize(v.id(), v.email()).statusCode()).isEqualTo(200);

        String emailAfterFirst = users.findById(v.id()).orElseThrow().getEmail();
        long usersBefore = users.count();
        long adminId = users.findByEmail(ADMIN).orElseThrow().getId();

        // Повтор від адміна (сам користувач уже не може увійти)
        HttpResponse<String> again = anonymize(v.id(), ADMIN);

        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(again.body()).contains("ALREADY_ANONYMIZED");
        assertThat(users.findById(v.id()).orElseThrow().getEmail()).isEqualTo(emailAfterFirst);
        assertThat(users.count()).isEqualTo(usersBefore);
        assertNoPii(dumpAllSources(v.id()), v);

        List<AuditEvent> trail = audit.findBySubjectIdOrderByCreatedAtAsc(v.id());
        assertThat(trail).hasSize(2);
        assertThat(trail.get(0).getResult()).isEqualTo("ANONYMIZED");
        assertThat(trail.get(1).getResult()).isEqualTo("ALREADY_ANONYMIZED");
        assertThat(trail.get(1).getActorId()).isEqualTo(adminId);
    }

    @Test
    void otherUser_isForbidden_andNothingChanges() throws Exception {
        Fixture v = createVictim();

        HttpResponse<String> r = anonymize(v.id(), BOB);

        assertThat(r.statusCode()).isEqualTo(403);
        assertThat(r.body()).isEmpty();
        AppUser user = users.findById(v.id()).orElseThrow();
        assertThat(user.getAnonymizedAt()).isNull();
        assertThat(user.getEmail()).isEqualTo(v.email());
        assertThat(orders.findByUserId(v.id())).hasSize(2);
    }

    @Test
    void getRequest_neverAnonymizes() throws Exception {
        Fixture v = createVictim();

        HttpResponse<String> r = send("GET", "/api/users/" + v.id() + "/anonymize", v.email());

        assertThat(r.statusCode()).isIn(404, 405);
        assertThat(users.findById(v.id()).orElseThrow().getAnonymizedAt()).isNull();
    }

    @Test
    void unauthenticated_isRejected_andNothingChanges() throws Exception {
        Fixture v = createVictim();

        HttpResponse<String> r = anonymize(v.id(), null);

        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(users.findById(v.id()).orElseThrow().getAnonymizedAt()).isNull();
    }

    @Test
    void nonexistentSubject_isNotRevealedToOrdinaryUsers() throws Exception {
        HttpResponse<String> asUser = anonymize(999999, BOB);
        HttpResponse<String> asAdmin = anonymize(999999, ADMIN);

        assertThat(asUser.statusCode()).isEqualTo(403);
        assertThat(asUser.body()).isEmpty();
        assertThat(asAdmin.statusCode()).isEqualTo(404);
    }
}