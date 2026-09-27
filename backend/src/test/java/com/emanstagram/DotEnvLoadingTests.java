package com.emanstagram;

import com.emanstagram.config.EmanstagramProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that {@code backend/.env} is genuinely read.
 *
 * <p>Kept in its own class on purpose. Each test here boots an extra
 * application context, and Spring's test context cache treats those as
 * separate lifecycles. When they shared a class with the repository tests,
 * closing one of these contexts tore down the in-memory database the others
 * were still using, and they failed with 'Table "USERS" not found'.
 *
 * <p>Why this matters: Spring Boot does not read a .env file on its own. It
 * only does because application.yml declares
 * {@code spring.config.import: "optional:file:.env[.properties]"}. Without
 * that line a correctly filled-in .env is silently ignored, every secret
 * falls back to the placeholder default, and the failure surfaces much later
 * as a confusing runtime error rather than an obvious config problem.
 */
class DotEnvLoadingTests {

    /**
     * Must be at least 32 bytes: JwtService rejects shorter secrets, and that
     * guard failing would mask the very thing under test.
     */
    private static final String TEST_SECRET = "from-dotenv-file-0123456789abcdefXYZ";

    @Test
    void dotEnvFileOverridesTheApplicationYmlDefault() throws IOException {

        // Written to the working directory because that is where
        // `spring.config.import: optional:file:.env` looks, and where a
        // developer would really put it.
        Path dotEnv = Path.of(".env");
        boolean existedBefore = Files.exists(dotEnv);
        byte[] original = existedBefore ? Files.readAllBytes(dotEnv) : null;

        try {
            Files.writeString(dotEnv, "JWT_SECRET=" + TEST_SECRET + "\n");

            try (ConfigurableApplicationContext context = boot("dotenv_present_db")) {
                var jwt = context.getBean(EmanstagramProperties.class);
                assertThat(jwt.jwt().secret())
                        .as("value from .env must override the application.yml default")
                        .isEqualTo(TEST_SECRET);
            }
        } finally {
            // Never leave a test secret behind, and never clobber a real one.
            if (original != null) {
                Files.write(dotEnv, original);
            } else {
                Files.deleteIfExists(dotEnv);
            }
        }
    }

    @Test
    void missingDotEnvFallsBackToDefaults() {

        if (Files.exists(Path.of(".env"))) {
            // A developer has real credentials here. Deleting or overwriting
            // them to satisfy a test would be far worse than skipping it.
            return;
        }

        try (ConfigurableApplicationContext context = boot("dotenv_absent_db")) {
            var jwt = context.getBean(EmanstagramProperties.class);
            assertThat(jwt.jwt().secret())
                    .as("must fall back rather than fail when .env is absent")
                    .startsWith("dev-only-secret");
        }
    }

    /**
     * Boots an isolated context. DB_CLOSE_DELAY=0 matters: the default in
     * application-local.yml is -1, which keeps an in-memory database alive
     * after the context that created it has closed.
     */
    private static ConfigurableApplicationContext boot(String dbName) {
        return new SpringApplicationBuilder(EmanstagramApplication.class)
                .profiles("local")
                .properties(
                        "spring.datasource.url=jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=0",
                        "spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.flyway.enabled=false")
                .run();
    }
}
