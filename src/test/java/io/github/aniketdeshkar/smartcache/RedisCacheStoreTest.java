package io.github.aniketdeshkar.smartcache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RedisCacheStoreTest {
  @Test
  void serializesEntryAndUsesRemainingStaleDeadlineAsRedisTtl() {
    var redis = new FakeRedis();
    var store =
        new RedisCacheStore<>(
            redis,
            new ObjectMapper(),
            String.class,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            "app:");
    var entry =
        new CacheEntry<>(
            Optional.of("value"), Instant.EPOCH.plusSeconds(5), Instant.EPOCH.plusSeconds(15));
    store.put("key", entry);
    assertEquals(Duration.ofSeconds(15), redis.ttl);
    assertEquals("value", store.get("key").orElseThrow().value().orElseThrow());
    store.invalidate("key");
    assertTrue(store.get("key").isEmpty());
  }

  private static final class FakeRedis implements RedisCommands {
    private final Map<String, String> values = new HashMap<>();
    private Duration ttl;

    public Optional<String> get(String key) {
      return Optional.ofNullable(values.get(key));
    }

    public void set(String key, String value, Duration ttl) {
      values.put(key, value);
      this.ttl = ttl;
    }

    public void delete(String key) {
      values.remove(key);
    }
  }
}
