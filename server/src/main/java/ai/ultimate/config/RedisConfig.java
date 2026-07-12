package ai.ultimate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for Jarvis.
 *
 * WHY EXPLICIT CONFIG:
 * Spring Boot auto-configures ReactiveRedisTemplate<String, String>
 * but names it "reactiveRedisTemplate" NOT
 * "reactiveStringRedisTemplate".
 *
 * SessionCacheService uses:
 * @Qualifier("reactiveStringRedisTemplate")
 *
 * We create an explicit bean with that exact name
 * so Spring can inject it correctly.
 *
 * SERIALIZATION:
 * Both key and value use StringRedisSerializer.
 * This means all data stored as plain UTF-8 strings.
 * SessionCacheService uses JSON Lines format for values.
 */
@Configuration
public class RedisConfig {

    /**
     * Reactive Redis template for String key-value operations.
     *
     * Bean name: "reactiveStringRedisTemplate"
     * Matches @Qualifier in SessionCacheService.
     *
     * Both key and value serialized as plain strings.
     * No JSON serialization at Redis level —
     * SessionCacheService handles JSON manually.
     *
     * @param factory auto-configured by Spring Boot
     *                from spring.data.redis.* properties
     */
    @Bean("reactiveStringRedisTemplate")
    public ReactiveRedisTemplate<String, String>
    reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        StringRedisSerializer serializer =
                new StringRedisSerializer();

        RedisSerializationContext<String, String> context =
                RedisSerializationContext
                        .<String, String>newSerializationContext(
                                serializer)
                        .key(serializer)
                        .value(serializer)
                        .hashKey(serializer)
                        .hashValue(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(
                factory, context);
    }
}