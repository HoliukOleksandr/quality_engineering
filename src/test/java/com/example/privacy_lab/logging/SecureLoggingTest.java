package com.example.privacy_lab.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureLoggingTest {

    // Усі значення СИНТЕТИЧНІ
    private static final String EMAIL = "alice@example.test";
    private static final String PHONE = "+380000000001";
    private static final String PASSWORD = "S3cretPass!";
    private static final String TOKEN = "synthetic-access-token-abc123";

    record Credentials(String email, String password) {
    }

    record SignupDto(String phone, Credentials credentials) {
    }

    /**
     * Перехоплює фінальний текст, який видав layout (те, що потрапило б у консоль
     * чи файл).
     */
    private static class CapturingAppender extends AppenderBase<ILoggingEvent> {
        private final Layout<ILoggingEvent> layout;
        final StringBuilder buffer = new StringBuilder();

        CapturingAppender(Layout<ILoggingEvent> layout) {
            this.layout = layout;
        }

        @Override
        protected void append(ILoggingEvent event) {
            buffer.append(layout.doLayout(event));
        }
    }

    private StringBuilder captured;

    private Logger loggerWith(PatternLayout layout) {
        LoggerContext ctx = new LoggerContext();
        ctx.start();

        layout.setContext(ctx);
        layout.setPattern("%-5level %logger{20} - %msg%n");
        layout.start();
        assertThat(layout.isStarted()).as("layout повинен запуститися").isTrue();

        CapturingAppender appender = new CapturingAppender(layout);
        appender.setContext(ctx);
        appender.start();
        captured = appender.buffer;

        Logger logger = ctx.getLogger("secure-test");
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(appender);
        return logger;
    }

    private String output() {
        return captured.toString();
    }

    private void assertNoLeak(String log) {
        assertThat(log).as("лог не повинен бути порожнім").isNotBlank();
        assertThat(log)
                .doesNotContain(EMAIL)
                .doesNotContain(PHONE)
                .doesNotContain(PASSWORD)
                .doesNotContain(TOKEN);
    }

    @Test
    void plainText_masksPiiAndRedactsSecrets() {
        Logger log = loggerWith(new SanitizingPatternLayout());
        log.info("Processing {} phone {} password={} accessToken={}", EMAIL, PHONE, PASSWORD, TOKEN);

        assertNoLeak(output());
        assertThat(output())
                .contains("a***@example.test")
                .contains("password=[REDACTED]")
                .contains("accessToken=[REDACTED]");
    }

    @Test
    void json_keepsFormatAndDiagnosticFields() {
        Logger log = loggerWith(new SanitizingPatternLayout());
        log.info("correlationId=corr-123 payload={\"op\":\"signup\",\"email\":\"" + EMAIL
                + "\",\"password\":\"" + PASSWORD + "\",\"accessToken\":\"" + TOKEN + "\"}");

        assertNoLeak(output());
        assertThat(output())
                .contains("correlationId=corr-123")
                .contains("\"op\":\"signup\"")
                .contains("\"password\":\"[REDACTED]\"");
    }

    @Test
    void nestedDto_isSanitized() {
        Logger log = loggerWith(new SanitizingPatternLayout());
        log.info("DTO: {}", new SignupDto(PHONE, new Credentials(EMAIL, PASSWORD)));

        assertNoLeak(output());
    }

    @Test
    void exceptionMessageAndStackTrace_areSanitized() {
        Logger log = loggerWith(new SanitizingPatternLayout());
        log.error("Operation failed", new IllegalStateException(
                "User " + EMAIL + " phone " + PHONE + " token=" + TOKEN + " password=" + PASSWORD));

        assertNoLeak(output());
        assertThat(output()).contains("IllegalStateException");
    }

    /**
     * Доводить, що перевірка вміє падати: без санітизації значення потрапляють у
     * лог.
     */
    @Test
    void withoutSanitizer_leaksAreDetected() {
        Logger log = loggerWith(new PatternLayout());
        log.info("Processing {} phone {} password={} token={}", EMAIL, PHONE, PASSWORD, TOKEN);

        assertThat(output()).contains(EMAIL).contains(PHONE).contains(PASSWORD).contains(TOKEN);
    }

    @Test
    void timestampsAndOrdinaryNumbersAreNotMasked() {
        String line = "2026-11-01 19:08:41.056 INFO pid 6988 order 42 total 150.00";
        assertThat(PiiSanitizer.sanitize(line)).isEqualTo(line);
    }
}