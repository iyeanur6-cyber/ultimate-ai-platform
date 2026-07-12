package ai.ultimate.cli;

import lombok.extern.slf4j.Slf4j;
import org.jline.reader.LineReader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Displays Jarvis banner on startup.
 * Checks for first-run via HTTP (not direct DB).
 * This avoids race condition with Flyway migrations.
 */
@Slf4j
@Component
@Order(1)
public class JarvisBanner implements ApplicationRunner {

    private static final String BANNER_SHOWN_KEY =
            "jarvis.banner.shown";

    private static final String BANNER = """

            +==============================================+
            |                                            |
            |       JARVIS AI PLATFORM v0.1.0            |
            |                                            |
            |  Local AI | Spring Boot 4 | Java 21        |
            |                                            |
            +--------------------------------------------+
            |  help      - all commands                  |
            |  login     - authenticate                  |
            |  chat      - talk to Jarvis                |
            |  status    - system health                 |
            +============================================+
            """;

    private final Environment environment;
    private final CliStateManager state;
    private final CliHttpClient http;
    private final LineReader lineReader;

    // NO UserRepository here — avoids Flyway race condition
    public JarvisBanner(
            Environment environment,
            CliStateManager state,
            CliHttpClient http,
            @Lazy LineReader lineReader) {
        this.environment = environment;
        this.state = state;
        this.http = http;
        this.lineReader = lineReader;
    }

    @Override
    public void run(ApplicationArguments args) {

        // Skip in test/non-interactive mode
        String interactive = environment.getProperty(
                "spring.shell.interactive.enabled",
                "true");
        if ("false".equals(interactive)) {
            return;
        }

        // Show banner once per JVM process
        if (System.getProperty(BANNER_SHOWN_KEY) == null) {
            System.setProperty(BANNER_SHOWN_KEY, "true");
            System.out.println(BANNER);
        }

        // Check first-run via HTTP (safe, no DB directly)
        // Wait for server to be ready first
        checkFirstRunViaHttp();
    }

    private void checkFirstRunViaHttp() {
        try {
            // Wait for Netty server to be ready
            Thread.sleep(1000);

            if (!http.isServerReachable()) {
                // Server not ready yet — skip
                return;
            }

            if (!state.isLoggedIn()) {
                System.out.println(
                        "  Tip: Type 'login' to sign in.");
                System.out.println(
                        "       Type 'setup' to create "
                                + "your first account.");
                System.out.println();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("Banner check skipped: {}",
                    e.getMessage());
        }
    }
}