package io.github.aniketdeshkar.smartcache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

public final class RedisCacheStore<V> implements CacheStore<V> {
  private final RedisCommands redis;
  private final ObjectMapper mapper;
  private final JavaType type;
  private final Clock clock;
  private final String prefix;

  public RedisCacheStore(
      RedisCommands redis, ObjectMapper mapper, Class<V> valueType, Clock clock, String prefix) {
    this.redis = redis;
    this.mapper = mapper.findAndRegisterModules();
    this.type = mapper.getTypeFactory().constructParametricType(CacheEntry.class, valueType);
    this.clock = clock;
    this.prefix = prefix;
  }

  public Optional<CacheEntry<V>> get(String key) {
    return redis
        .get(prefix + key)
        .map(
            value -> {
              try {
                return mapper.readValue(value, type);
              } catch (Exception error) {
                throw new IllegalStateException("invalid Redis cache entry", error);
              }
            });
  }

  public void put(String key, CacheEntry<V> entry) {
    try {
      Duration remaining = Duration.between(clock.instant(), entry.staleUntil());
      if (!remaining.isPositive()) return;
      redis.set(prefix + key, mapper.writeValueAsString(entry), remaining);
    } catch (Exception error) {
      throw new IllegalStateException("Redis cache write failed", error);
    }
  }

  public void invalidate(String key) {
    redis.delete(prefix + key);
  }
}
