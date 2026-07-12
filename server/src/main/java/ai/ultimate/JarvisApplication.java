package ai.ultimate;

import ai.ultimate.config.JarvisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
@EnableConfigurationProperties(JarvisProperties.class)
public class JarvisApplication {

    public static void main(String[] args) {

        // Force UTF-8 for Windows terminal
        try {
            System.setOut(new PrintStream(
                    System.out, true,
                    StandardCharsets.UTF_8));
            System.setErr(new PrintStream(
                    System.err, true,
                    StandardCharsets.UTF_8));
        } catch (Exception ignored) {}

        SpringApplication.run(
                JarvisApplication.class, args);
    }
}