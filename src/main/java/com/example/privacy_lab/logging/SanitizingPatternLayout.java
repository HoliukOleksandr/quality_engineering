package com.example.privacy_lab.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Централізований механізм: санітизується ГОТОВИЙ рядок логу
 * (повідомлення, параметри, toString об'єктів і stack trace винятків).
 */
public class SanitizingPatternLayout extends PatternLayout {

    @Override
    public String doLayout(ILoggingEvent event) {
        return PiiSanitizer.sanitize(super.doLayout(event));
    }
}