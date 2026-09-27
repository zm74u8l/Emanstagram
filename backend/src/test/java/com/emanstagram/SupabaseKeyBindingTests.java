package com.emanstagram;

import com.emanstagram.common.ApiException;
import com.emanstagram.config.EmanstagramProperties;
import com.emanstagram.storage.SupabaseStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the backend copes with a missing or unconfigured Supabase key.
 *
 * <p>That {@code SUPABASE_SECRET_KEY} is actually read from {@code .env} is
 * already covered by {@link DotEnvLoadingTests}, which writes a real file and
 * boots the app against it. Trying to re-test the binding here with
 * {@code System.setProperty} does not work: the {@code spring.config.import}
 * in application.yml registers {@code .env} as a ConfigData source that sits
 * above SystemProperties, so a developer's real {@code .env} would silently
 * win and the assertion would be testing nothing.
 *
 * <p>What is worth pinning here is the behaviour when the key is absent.
 * Getting this wrong is what turns a clear "storage is not configured"
 * message into an opaque 401 from Supabase at upload time.
 */
class SupabaseKeyBindingTests {

    @Test
    void noKeyMeansStorageIsNotConfigured() {
        withDotEnv("", context -> {
            var storage = context.getBean(SupabaseStorageService.class);
            assertThat(storage.isConfigured())
                    .as("an absent key must not look like a working setup")
                    .isFalse();
        });
    }

    @Test
    void requireConfiguredFailsLoudlyRatherThanUploadingAsAnonymous() {
        withDotEnv("", context -> {
            var storage = context.getBean(SupabaseStorageService.class);

            // Upload endpoints call this. It must produce a clear 503 naming
            // the cause, not silently attempt an unauthenticated upload.
            assertThat(catchThrowable(storage::requireConfigured))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("not configured");
        });
    }

    @Test
    void publicUrlIsNullWhenUnconfiguredSoCallersCanFallback() {
        withDotEnv("", context -> {
            var storage = context.getBean(SupabaseStorageService.class);

            // Returning a URL built from an empty base would produce a broken
            // <img> tag. Null lets the UI render an identicon instead.
            assertThat(storage.publicUrl("posts/abc.webp"))
                    .as("no key means no resolvable CDN URL")
                    .isNull();
        });
    }

    /**
     * The end-to-end check: a URL and key present only in a {@code .env} file
     * make storage report itself configured, and both values bind verbatim.
     */
    @Test
    void urlAndKeyInDotEnvArePickedUp() {
        // Plain concatenation rather than a text block. A text block strips
        // incidental indentation based on the closing delimiter, which made
        // the earlier version of this test emit a .env whose lines were not
        // what they looked like on screen.
        String env = "SUPABASE_URL=https://fake-project.supabase.co\n"
                + "SUPABASE_SECRET_KEY=sb_secret_fake_for_test\n";

        withDotEnv(env, context -> {
            var properties = context.getBean(EmanstagramProperties.class);

            assertThat(properties.storage().url())
                    .as("the URL from .env must bind")
                    .isEqualTo("https://fake-project.supabase.co");
            assertThat(properties.storage().serviceRoleKey())
                    .as("the secret key from .env must bind")
                    .isEqualTo("sb_secret_fake_for_test");

            assertThat(context.getBean(SupabaseStorageService.class).isConfigured())
                    .as("a key in .env must enable storage")
                    .isTrue();
        });
    }

    /**
     * The legacy {@code service_role} JWT is just a string to this backend,
     * so it must bind through the same variable as the newer {@code sb_secret_}.
     */
    @Test
    void legacyServiceRoleJwtBindsThroughTheSameVariable() {
        String env = "SUPABASE_URL=https://fake-project.supabase.co\n"
                + "SUPABASE_SECRET_KEY=eyJfake-legacy-service-role-jwt\n";

        withDotEnv(env, context -> {
            var properties = context.getBean(EmanstagramProperties.class);
            assertThat(properties.storage().serviceRoleKey())
                    .as("a service_role JWT must bind identically")
                    .isEqualTo("eyJfake-legacy-service-role-jwt");
        });
    }

    private interface Scenario {
        /** Receives a booted, already-open context to assert against. */
        void run(ConfigurableApplicationContext context);
    }

    /**
     * Replaces {@code .env} for the duration of a scenario, then restores it.
     *
     * <p>Every test goes through this rather than relying on ambient config,
     * so results never depend on what the developer has configured locally.
     * The real file is always put back, including when the scenario throws.
     */
    private static void withDotEnv(String contents, Scenario scenario) {
        Path dotEnv = Path.of(".env");

        try {
            boolean existed = Files.exists(dotEnv);
            String original = existed ? Files.readString(dotEnv) : null;

            try {
                // Written BEFORE the context boots, because the context reads
                // it during startup.
                Files.writeString(dotEnv, contents);

                try (ConfigurableApplicationContext context = boot()) {
                    scenario.run(context);
                }
            } finally {
                if (original != null) {
                    Files.writeString(dotEnv, original);
                } else {
                    Files.deleteIfExists(dotEnv);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to stage .env for the test", e);
        }
    }

    /**
     * Boots a context against a private in-memory database.
     *
     * <p>The {@code testrun} property exists purely to defeat Spring's context
     * cache. Without it, {@code SpringApplicationBuilder} hands back the first
     * context booted in this class, so only the first test would observe the
     * {@code .env} it staged and every later one would assert against stale
     * values. A unique value per call makes each boot genuinely new.
     *
     * <p>Deliberately does not override {@code spring.config.import}. An
     * earlier version pointed it at a nonexistent file to dodge the
     * developer's real .env, and that backfired: the override replaced the
     * import from application.yml, so .env was ignored entirely.
     */
    private static ConfigurableApplicationContext boot() {
        String unique = java.util.UUID.randomUUID().toString();

        return new SpringApplicationBuilder(EmanstagramApplication.class)
                .profiles("local")
                .properties(
                        "spring.datasource.url=jdbc:h2:mem:sbkey_" + unique
                                + ";DB_CLOSE_DELAY=0",
                        "spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.flyway.enabled=false",
                        "testrun=" + unique)
                .run();
    }
}
