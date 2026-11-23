package com.example.privacy_lab.consent;

import com.example.privacy_lab.anonymization.AnonymizationService;
import com.example.privacy_lab.domain.AppUser;
import com.example.privacy_lab.export.PersonalDataExport;
import com.example.privacy_lab.export.PersonalDataService;
import com.example.privacy_lab.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsentLifecycleIntegrationTest {

    @Autowired
    private AppUserRepository users;
    @Autowired
    private ConsentService consentService;
    @Autowired
    private ConsentPolicyGate gate;
    @Autowired
    private AnonymizationService anonymization;
    @Autowired
    private PersonalDataService export;
    @Autowired
    private PasswordEncoder encoder;

    // Усі дані СИНТЕТИЧНІ
    private long createUser() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        AppUser u = new AppUser();
        u.setEmail("lifecycle-" + tag + "@example.test");
        u.setFullName("Lifecycle Fixture " + tag);
        u.setPasswordHash(encoder.encode("Passw0rd!"));
        return users.save(u).getId();
    }

    @Test
    void export_containsConsentRecordsWithEvidenceMetadata() {
        long id = createUser();
        consentService.grant(id, ConsentPurpose.MARKETING_EMAIL, "API");

        PersonalDataExport data = export.export(id).orElseThrow();

        assertThat(data.consents()).hasSize(1);
        PersonalDataExport.ConsentRecord record = data.consents().get(0);
        assertThat(record.purpose()).isEqualTo("MARKETING_EMAIL");
        assertThat(record.status()).isEqualTo("GRANTED");
        assertThat(record.policyVersion()).isEqualTo("v1");
        assertThat(record.source()).isEqualTo("API");
        assertThat(record.grantedAt()).isNotNull();
        assertThat(record.withdrawnAt()).isNull();
    }

    @Test
    void anonymization_revokesAllActiveConsents_soPolicyGateDenies() {
        long id = createUser();
        consentService.grant(id, ConsentPurpose.MARKETING_EMAIL, "API");
        consentService.grant(id, ConsentPurpose.OPTIONAL_ANALYTICS, "API");
        assertThat(gate.isAllowed(id, ConsentPurpose.MARKETING_EMAIL)).isTrue();
        assertThat(gate.isAllowed(id, ConsentPurpose.OPTIONAL_ANALYTICS)).isTrue();

        AnonymizationService.Outcome outcome = anonymization.anonymize(id, id);

        assertThat(outcome).isEqualTo(AnonymizationService.Outcome.ANONYMIZED);
        assertThat(gate.isAllowed(id, ConsentPurpose.MARKETING_EMAIL)).isFalse();
        assertThat(gate.isAllowed(id, ConsentPurpose.OPTIONAL_ANALYTICS)).isFalse();
    }
}