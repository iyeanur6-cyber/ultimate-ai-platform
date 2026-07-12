package ai.ultimate.cli;

import ai.ultimate.security.auth.request.LoginRequest;
import ai.ultimate.security.auth.response.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.LineReader;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AuthCommands {

    private final CliStateManager state;
    private final CliHttpClient http;
    private final LineReader lineReader;

    /**
     * Single constructor — Spring Framework 7
     * automatically uses this for DI.
     *
     * @Lazy on LineReader breaks the circular
     * dependency with CommandRegistry:
     * authCommands → lineReader → commandCompleter
     * → commandRegistry → authCommands
     */
    public AuthCommands(
            CliStateManager state,
            CliHttpClient http,
            @Lazy LineReader lineReader) {
        this.state = state;
        this.http = http;
        this.lineReader = lineReader;
    }

    @Command(name = "login",
            description = "Login to Jarvis")
    public String login() {

        if (state.isLoggedIn()) {
            return "Already logged in as: "
                    + state.getUsername()
                    + ". Type 'logout' first.";
        }

        if (!http.isServerReachable()) {
            return "Cannot reach server. Is Jarvis running?";
        }

        String username = lineReader.readLine("Username: ");
        String password = lineReader.readLine("Password: ", '*');

        try {
            Map<String, Object> response = http.postForMap(
                    "/api/v1/auth/login",
                    new ai.jarvis.security.auth.request
                            .LoginRequest(
                            username.trim(),
                            password.trim()));

            if (response == null) {
                return "Login failed: null response";
            }

            String accessToken = (String) response
                    .get("accessToken");

            @SuppressWarnings("unchecked")
            Map<String, Object> user =
                    (Map<String, Object>) response.get("user");

            if (accessToken == null || user == null) {
                return "Login failed: unexpected format\n"
                        + "Full response: " + response;
            }

            state.setAccessToken(accessToken);
            state.setUsername((String) user.get("username"));
            state.setRole((String) user.get("role"));

            String userId = (String) user.get("userId");
            if (userId != null) {
                state.setUserId(
                        java.util.UUID.fromString(userId));
            }

            String displayName = (String) user.get("displayName");
            if (displayName == null) displayName = state.getUsername();

            return "Welcome back, " + displayName
                    + "! (" + state.getRole() + ")";

        } catch (Exception e) {
            return "Login exception: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage();
        }
    }

    @Command(
            name = "logout",
            description = "Logout from Jarvis"
    )
    public String logout() {
        if (!state.isLoggedIn()) {
            return "Not logged in.";
        }
        String name = state.getUsername();
        state.clear();
        return "Goodbye, " + name + "!";
    }

    @Command(
            name = "whoami",
            description = "Show current user info"
    )
    public String whoami() {
        if (!state.isLoggedIn()) {
            return "Not logged in. Type: login";
        }
        return "\nCurrent User\n"
                + "--------------------------\n"
                + "Username: "
                + state.getUsername() + "\n"
                + "Role:     "
                + state.getRole() + "\n"
                + "ID:       "
                + state.getUserId().toString()
                .substring(0, 8) + "...\n";
    }

    private String extractMessage(Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("401")) {
            return "Invalid username or password";
        }
        if (msg != null
                && msg.contains("Connection refused")) {
            return "Server not running on port 8080";
        }
        return msg != null ? msg : "Unknown error";
    }
}