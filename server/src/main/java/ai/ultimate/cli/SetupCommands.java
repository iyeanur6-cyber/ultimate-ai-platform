package ai.ultimate.cli;

import lombok.extern.slf4j.Slf4j;
import org.jline.reader.LineReader;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * First-run setup command.
 *
 * Usage: jarvis:> setup
 *
 * Creates the first admin account.
 * Safe to run anytime — will reject if user exists.
 */
@Slf4j
@Component
public class SetupCommands {

    private final CliStateManager state;
    private final CliHttpClient http;
    private final LineReader lineReader;

    public SetupCommands(
            CliStateManager state,
            CliHttpClient http,
            @Lazy LineReader lineReader) {
        this.state = state;
        this.http = http;
        this.lineReader = lineReader;
    }

    @Command(
            name = "setup",
            description = "Create your first admin account "
                    + "(first-run setup)"
    )
    public String setup() {

        if (!http.isServerReachable()) {
            return "Server not reachable. "
                    + "Is Jarvis running?";
        }

        if (state.isLoggedIn()) {
            return "Already logged in as: "
                    + state.getUsername()
                    + "\nType 'logout' first.";
        }

        System.out.println();
        System.out.println(
                "+--------------------------------------------+");
        System.out.println(
                "|           CREATE ADMIN ACCOUNT             |");
        System.out.println(
                "+--------------------------------------------+");
        System.out.println();

        try {
            String username = lineReader.readLine(
                    "Username: ");

            if (username == null || username.isBlank()) {
                return "Username cannot be empty.";
            }

            if (username.trim().length() < 3) {
                return "Username must be at least "
                        + "3 characters.";
            }

            String password = lineReader.readLine(
                    "Password: ", '*');

            if (password == null
                    || password.trim().length() < 8) {
                return "Password must be at least "
                        + "8 characters.";
            }

            String confirm = lineReader.readLine(
                    "Confirm Password: ", '*');

            if (!password.trim().equals(confirm.trim())) {
                return "Passwords do not match. "
                        + "Type 'setup' to try again.";
            }

            String displayName = lineReader.readLine(
                    "Display Name (Enter to skip): ");

            if (displayName == null
                    || displayName.isBlank()) {
                displayName = username;
            }

            // Call register endpoint
            Map<String, String> body = new HashMap<>();
            body.put("username", username.trim());
            body.put("email",
                    username.trim() + "@jarvis.local");
            body.put("password", password.trim());
            body.put("displayName", displayName.trim());

            http.post(
                    "/api/v1/auth/register",
                    body, Object.class);

            return "\nAccount created successfully!\n"
                    + "Username: " + username.trim() + "\n"
                    + "Role:     ADMIN (first account)\n\n"
                    + "Type 'login' to sign in.";

        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("409")) {
                return "Username already exists. "
                        + "Try a different username.";
            }
            if (msg != null && msg.contains("401")) {
                return "Setup failed. "
                        + "Check server logs.";
            }
            return "Setup failed: " + msg;
        }
    }
}