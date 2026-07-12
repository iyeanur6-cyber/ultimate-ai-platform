package ai.ultimate.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI model configuration.
 *
 * WHY @Primary:
 * Even though GoogleGenAiChatAutoConfiguration is excluded,
 * we keep @Primary as a safety net in case other
 * ChatModel beans are added in future phases
 * (e.g. OpenRouter - Issue #7).
 *
 * This ensures ChatClient.Builder always defaults
 * to OllamaChatModel without ambiguity.
 */
@Configuration
public class AiConfig {

    @Bean
    @Primary
    public ChatModel primaryChatModel(
            OllamaChatModel ollamaChatModel) {
        return ollamaChatModel;
    }
}