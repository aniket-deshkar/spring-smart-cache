package io.github.aniketdeshkar.smartcache;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

public final class SmartCache<V> {
  private final CacheStore<V> l1;
  private final CacheStore<V> l2;
  private final CachePolicy policy;
  private final Clock clock;
  private final DoubleSupplier random;
  private final Executor executor;
  private final CacheMetrics metrics;
  private final ConcurrentHashMap<String, CompletableFuture<Optional<V>>> loads =
      new ConcurrentHashMap<>();

  public SmartCache(
      CacheStore<V> l1,
      CacheStore<V> l2,
      CachePolicy policy,
      Clock clock,
      DoubleSupplier random,
      Executor executor,
      CacheMetrics metrics) {
    this.l1 = l1;
    this.l2 = l2;
    this.policy = policy;
    this.clock = clock;
    this.random = random;
    this.executor = executor;
    this.metrics = metrics;
  }

  public Optional<V> get(String key, Function<String, Optional<V>> loader) {
    var now = clock.instant();
    var entry = l1.get(key).or(() -> l2 == null ? Optional.empty() : l2.get(key));
    if (entry.isPresent() && entry.get().fresh(now)) {
      l1.put(key, entry.get());
      metrics.record("hit");
      return entry.get().value();
    }
    if (entry.isPresent() && entry.get().staleUsable(now)) {
      metrics.record("stale");
      refresh(key, loader);
      return entry.get().value();
    }
    metrics.record("miss");
    return load(key, loader).join();
  }

  private void refresh(String key, Function<String, Optional<V>> loader) {
    load(key, loader);
  }

  private CompletableFuture<Optional<V>> load(String key, Function<String, Optional<V>> loader) {
    CompletableFuture<Optional<V>> candidate = new CompletableFuture<>();
    CompletableFuture<Optional<V>> existing = loads.putIfAbsent(key, candidate);
    if (existing != null) return existing;
    executor.execute(
        () -> {
          try {
            candidate.complete(doLoad(key, loader));
          } catch (Throwable error) {
            candidate.completeExceptionally(error);
          } finally {
            loads.remove(key, candidate);
          }
        });
    return candidate;
  }

  private Optional<V> doLoad(String key, Function<String, Optional<V>> loader) {
    Optional<V> value = loader.apply(key);
    Duration base = value.isPresent() ? policy.ttl() : policy.negativeTtl();
    double factor =
        1 - policy.jitterFraction() + (2 * policy.jitterFraction() * random.getAsDouble());
    Duration ttl = Duration.ofNanos(Math.max(1, (long) (base.toNanos() * factor)));
    var fresh = clock.instant().plus(ttl);
    var entry = new CacheEntry<>(value, fresh, fresh.plus(policy.staleWhileRevalidate()));
    l1.put(key, entry);
    if (l2 != null) l2.put(key, entry);
    metrics.record("load");
    return value;
  }

  public void invalidate(String key) {
    l1.invalidate(key);
    if (l2 != null) l2.invalidate(key);
  }

  public void warm(Collection<String> keys, Function<String, Optional<V>> loader) {
    keys.forEach(key -> load(key, loader).join());
  }
}
