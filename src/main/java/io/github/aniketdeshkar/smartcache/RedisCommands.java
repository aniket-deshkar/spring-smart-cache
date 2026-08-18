package io.github.aniketdeshkar.smartcache;

import java.time.Duration;
import java.util.Optional;

public interface RedisCommands {
  Optional<String> get(String key);

  void set(String key, String value, Duration ttl);

  void delete(String key);
}
