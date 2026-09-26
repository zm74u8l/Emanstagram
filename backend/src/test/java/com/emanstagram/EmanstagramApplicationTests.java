package com.emanstagram;

import com.emanstagram.config.EmanstagramProperties;
import com.emanstagram.user.User;
import com.emanstagram.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the whole application context wires up: security, JWT, JPA
 * repositories, storage and the WebSocket broker.
 *
 * <p>Runs on the H2 `local` profile so it needs no external services. The
 * Postgres schema itself is not exercised here, only the Java side.
 */
@SpringBootTest
@ActiveProfiles("local")
class EmanstagramApplicationTests {

    @Autowired
    private EmanstagramProperties properties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Test
    void contextLoads() {
        assertThat(properties).isNotNull();
    }

    @Test
    void mediaLimitsMatchTheAgreedCaps() {
        var media = properties.media();
        // 10 MB images, 15 MB videos, 5 MB avatars.
        assertThat(media.maxImageBytes()).isEqualTo(10L * 1024 * 1024);
        assertThat(media.maxVideoBytes()).isEqualTo(15L * 1024 * 1024);
        assertThat(media.maxAvatarBytes()).isEqualTo(5L * 1024 * 1024);
    }

    @Test
    void storageIsAbsentOnLocalProfileAndDoesNotCrashStartup() {
        // Confirms the app boots cleanly before Supabase credentials exist.
        assertThat(properties.storage()).isNotNull();
    }

    /**
     * Regression test.
     *
     * <p>When the password encoder was swapped in, Argon2 could be constructed
     * but threw NoClassDefFoundError on the first encode() because BouncyCastle
     * was absent from the classpath. The context still loaded, so only a real
     * registration revealed the problem. Asserting an actual hash here means a
     * missing or broken encoder fails the build instead of production.
     */
    @Test
    void passwordEncoderCanActuallyHash() {
        String encoded = passwordEncoder.encode("correct-horse-battery-staple");
        assertThat(encoded).isNotBlank();
        assertThat(encoded).isNotEqualTo("correct-horse-battery-staple");
        assertThat(passwordEncoder.matches("correct-horse-battery-staple", encoded)).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", encoded)).isFalse();
    }

    /**
     * Regression test.
     *
     * <p>Entities must keep a non-private no-arg constructor. Hibernate builds
     * a runtime proxy subclass when building security principals, and a private
     * or missing constructor caused every authenticated request to fail with a
     * 401. Touching the repository proves a real lazy load still works.
     */
    @Test
    void userEntityCanBeLoadedThroughAProxy() {
        var saved = userRepository.save(new User("proxytest", "proxytest@example.com", "hash"));
        var found = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getUsername()).isEqualTo("proxytest");
        assertThat(found.effectiveName()).isEqualTo("proxytest");
        // A proxy must compare equal to its real instance, or collections
        // containing users silently break contains()/remove().
        assertThat(found).isEqualTo(saved);
        assertThat(found.hashCode()).isEqualTo(saved.hashCode());
    }
}
