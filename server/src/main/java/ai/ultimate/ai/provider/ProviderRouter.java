package ai.ultimate.ai.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Routes AI requests to the correct provider.
 *
 * ROUTING LOGIC:
 * 1. Check Ollama availability (local, primary)
 * 2. If Ollama down → fallback to Gemini
 * 3. If both down → error with helpful message
 *
 * FIX (CI Job #81959644503):
 * Added @Qualifier("gemini") to constructor parameter.
 * Without this, Spring cannot resolve which AiProvider
 * bean to inject when multiple implementations exist.
 *
 * Both GeminiProvider and GeminiUnavailableProvider are
 * now named "gemini" via @Component("gemini").
 * Only ONE will be created (conditional beans).
 * @Qualifier tells Spring exactly which name to inject.
 */
@Slf4j
@Component
public class ProviderRouter {

    private final OllamaProvider ollamaProvider;
    private final AiProvider geminiProvider;

    /**
     * FIX: @Qualifier("gemini") added.
     *
     * WHY THIS FIXES THE CI FAILURE:
     * In CI: GEMINI_API_KEY is empty
     * → GeminiProvider NOT created (ConditionalOnExpression)
     * → GeminiUnavailableProvider IS created (@ConditionalOnMissingBean)
     * → Both are named "gemini" via @Component("gemini")
     * → @Qualifier("gemini") tells Spring to inject it
     * → ProviderRouter creates successfully ✅
     *
     * Locally with API key set:
     * → GeminiProvider IS created
     * → GeminiUnavailableProvider NOT created
     * → @Qualifier("gemini") injects GeminiProvider ✅
     */
    public ProviderRouter(
            OllamaProvider ollamaProvider,
            @Qualifier("gemini")            // ← ADD this
            AiProvider geminiProvider) {
        this.ollamaProvider = ollamaProvider;
        this.geminiProvider = geminiProvider;
        log.info(
                "ProviderRouter initialized: "
                        + "primary=ollama fallback={}",
                geminiProvider.getName()
                        + "("
                        + geminiProvider.getModelName()
                        + ")");
    }

    public Mono<AiProvider> route() {
        return ollamaProvider.isAvailable()
                .flatMap(ollamaUp -> {
                    if (ollamaUp) {
                        log.debug(
                                "Routing to: ollama ({})",
                                ollamaProvider.getModelName());
                        return Mono.just(
                                (AiProvider) ollamaProvider);
                    }

                    log.warn(
                            "Ollama unavailable. "
                                    + "Checking Gemini...");

                    return geminiProvider
                            .isAvailable()
                            .flatMap(geminiUp -> {
                                if (geminiUp) {
                                    log.info(
                                            "Routing to: "
                                                    + "gemini [FALLBACK]");
                                    return Mono.just(
                                            geminiProvider);
                                }
                                log.error(
                                        "All providers "
                                                + "unavailable");
                                return Mono.error(
                                        new RuntimeException(
                                                "No AI provider "
                                                        + "available.\n"
                                                        + "Fix: ollama serve"
                                        ));
                            });
                });
    }

    public Mono<AiProvider> routeTo(String name) {
        return switch (name.toLowerCase()) {
            case "ollama" ->
                    Mono.just((AiProvider) ollamaProvider);
            case "gemini" ->
                    Mono.just(geminiProvider);
            default -> Mono.error(
                    new RuntimeException(
                            "Unknown provider: " + name));
        };
    }
}