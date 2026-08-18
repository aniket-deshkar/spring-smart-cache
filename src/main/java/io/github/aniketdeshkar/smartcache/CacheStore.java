package io.github.aniketdeshkar.smartcache;

import java.util.Optional;

public interface CacheStore<V> {
  Optional<CacheEntry<V>> get(String key);

  void put(String key, CacheEntry<V> entry);

  void invalidate(String key);
}
