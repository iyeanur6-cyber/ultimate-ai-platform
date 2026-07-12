package ai.ultimate.ai.provider;

import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The provider abstraction contract.
 *
 * Every AI provider (Ollama, Gemini, OpenRouter...)
 * implements this interface.
 *
 * The AiOrchestrator ONLY knows this interface.
 * It never knows if it is talking to Ollama or Gemini.
 * That is the power of this abstraction.
 */
public interface AiProvider {

    /**
     * Stream chat response token by token.
     * Returns Flux<String> of tokens.
     */
    Flux<String> streamChat(Prompt prompt);

    /**
     * Check if this provider is available right now.
     * Non-blocking health check.
     */
    Mono<Boolean> isAvailable();

    /**
     * Provider identifier for logging/routing.
     * Examples: "ollama", "gemini"
     */
    String getName();

    /**
     * Model name this provider is using.
     * Examples: "llama3.1:8b", "gemini-1.5-flash"
     */
    String getModelName();
}