package com.example.privacy_lab.config;

import com.example.privacy_lab.domain.ActivityEvent;
import com.example.privacy_lab.domain.AppUser;
import com.example.privacy_lab.domain.CustomerOrder;
import com.example.privacy_lab.domain.UserSettings;
import com.example.privacy_lab.repository.ActivityEventRepository;
import com.example.privacy_lab.repository.AppUserRepository;
import com.example.privacy_lab.repository.CustomerOrderRepository;
import com.example.privacy_lab.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Усі дані СИНТЕТИЧНІ (домен .test, зарезервований діапазон IP 192.0.2.x). */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AppUserRepository users;
    private final UserSettingsRepository settings;
    private final CustomerOrderRepository orders;
    private final ActivityEventRepository events;
    private final PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        AppUser alice = user("alice@example.test", "Alice Testova", "+380000000001",
                "1 Test Street, Testville", "ROLE_USER");
        AppUser bob = user("bob@example.test", "Bob Sample", "+380000000002",
                "2 Sample Avenue, Sampletown", "ROLE_USER");
        user("admin@example.test", "Admin Synthetic", "+380000000003",
                "3 Admin Road, Adminville", "ROLE_ADMIN");

        alice.setResetToken("synthetic-reset-token-alice-123");
        alice.setFraudFlag(true);
        users.save(alice);

        settings(alice, "uk", "dark", "Europe/Kyiv");
        settings(bob, "en", "light", "Europe/Warsaw");

        order(alice, "1 Test Street, Testville", "+380000000001", "150.00");
        order(alice, "1 Test Street, Testville", "+380000000001", "89.90");
        order(bob, "2 Sample Avenue, Sampletown", "+380000000002", "42.50");

        event(alice, "LOGIN", "192.0.2.10");
        event(alice, "VIEW_ORDERS", "192.0.2.10");
        event(bob, "LOGIN", "192.0.2.20");
    }

    private AppUser user(String email, String name, String phone, String address, String role) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName(name);
        u.setPhone(phone);
        u.setAddress(address);
        u.setPasswordHash(encoder.encode("Passw0rd!"));
        u.setRole(role);
        return users.save(u);
    }

    private void settings(AppUser u, String lang, String theme, String tz) {
        UserSettings s = new UserSettings();
        s.setUser(u);
        s.setLanguage(lang);
        s.setTheme(theme);
        s.setTimeZone(tz);
        settings.save(s);
    }

    private void order(AppUser u, String address, String phone, String amount) {
        CustomerOrder o = new CustomerOrder();
        o.setUser(u);
        o.setShippingAddress(address);
        o.setShippingPhone(phone);
        o.setAmount(new BigDecimal(amount));
        orders.save(o);
    }

    private void event(AppUser u, String action, String ip) {
        ActivityEvent e = new ActivityEvent();
        e.setUser(u);
        e.setAction(action);
        e.setIpAddress(ip);
        events.save(e);
    }
}