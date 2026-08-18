package io.github.aniketdeshkar.smartcache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Optional;

public final class CaffeineCacheStore<V> implements CacheStore<V> {
  private final Cache<String, CacheEntry<V>> cache;

  public CaffeineCacheStore(long maximumSize) {
    if (maximumSize <= 0) throw new IllegalArgumentException("maximumSize must be positive");
    cache = Caffeine.newBuilder().maximumSize(maximumSize).build();
  }

  public Optional<CacheEntry<V>> get(String key) {
    return Optional.ofNullable(cache.getIfPresent(key));
  }

  public void put(String key, CacheEntry<V> entry) {
    cache.put(key, entry);
  }

  public void invalidate(String key) {
    cache.invalidate(key);
  }
}
