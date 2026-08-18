package io.github.aniketdeshkar.smartcache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SmartCacheTest {
  @Test
  void coalescesConcurrentMissesForOneKey() throws Exception {
    var loads = new AtomicInteger();
    var cache = cache(new MutableClock());
    try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new java.util.concurrent.CountDownLatch(1);
      var tasks =
          java.util.stream.IntStream.range(0, 20)
              .mapToObj(
                  i ->
                      callers.submit(
                          () -> {
                            start.await();
                            return cache.get(
                                "hot",
                                key -> {
                                  loads.incrementAndGet();
                                  try {
                                    Thread.sleep(20);
                                  } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                  }
                                  return Optional.of("value");
                                });
                          }))
              .toList();
      start.countDown();
      for (var task : tasks) assertEquals("value", task.get().orElseThrow());
    }
    assertEquals(1, loads.get());
  }

  @Test
  void staleValueReturnsWhileSingleRefreshRuns() throws Exception {
    var clock = new MutableClock();
    var loads = new AtomicInteger();
    var refreshed = new java.util.concurrent.CountDownLatch(1);
    var cache = cache(clock);
    assertEquals(
        "v1", cache.get("k", key -> Optional.of("v" + loads.incrementAndGet())).orElseThrow());
    clock.advance(Duration.ofSeconds(11));
    assertEquals(
        "v1",
        cache
            .get(
                "k",
                key -> {
                  var value = Optional.of("v" + loads.incrementAndGet());
                  refreshed.countDown();
                  return value;
                })
            .orElseThrow());
    assertTrue(refreshed.await(2, java.util.concurrent.TimeUnit.SECONDS));
    assertEquals(2, loads.get());
    assertEquals("v2", cache.get("k", key -> Optional.of("never")).orElseThrow());
  }

  @Test
  void negativeEntriesAreCached() {
    var loads = new AtomicInteger();
    var cache = cache(new MutableClock());
    assertTrue(
        cache
            .get(
                "missing",
                key -> {
                  loads.incrementAndGet();
                  return Optional.empty();
                })
            .isEmpty());
    assertTrue(
        cache
            .get(
                "missing",
                key -> {
                  loads.incrementAndGet();
                  return Optional.of("wrong");
                })
            .isEmpty());
    assertEquals(1, loads.get());
  }

  @Test
  void invalidationClearsBothLevels() {
    var l1 = new CaffeineCacheStore<String>(10);
    var l2 = new CaffeineCacheStore<String>(10);
    var clock = new MutableClock();
    var cache =
        new SmartCache<>(l1, l2, policy(), clock, () -> 0.5, Runnable::run, CacheMetrics.noOp());
    cache.get("k", key -> Optional.of("one"));
    cache.invalidate("k");
    assertEquals("two", cache.get("k", key -> Optional.of("two")).orElseThrow());
  }

  @Test
  void warmsKeysAndPublishesMetrics() {
    List<String> outcomes = new ArrayList<>();
    var cache =
        new SmartCache<>(
            new CaffeineCacheStore<>(10),
            null,
            policy(),
            new MutableClock(),
            () -> 0.5,
            Runnable::run,
            outcomes::add);
    cache.warm(List.of("a", "b"), key -> Optional.of(key));
    assertEquals("a", cache.get("a", key -> Optional.empty()).orElseThrow());
    assertEquals(List.of("load", "load", "hit"), outcomes);
  }

  @Test
  void validatesPoliciesAndStoreBounds() {
    assertThrows(IllegalArgumentException.class, () -> new CaffeineCacheStore<>(0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CachePolicy(Duration.ZERO, Duration.ZERO, Duration.ofSeconds(1), 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CachePolicy(Duration.ofSeconds(1), Duration.ZERO, Duration.ofSeconds(1), 1.1));
  }

  private static SmartCache<String> cache(MutableClock clock) {
    return new SmartCache<>(
        new CaffeineCacheStore<>(100),
        new CaffeineCacheStore<>(100),
        policy(),
        clock,
        () -> 0.5,
        Executors.newVirtualThreadPerTaskExecutor(),
        CacheMetrics.noOp());
  }

  private static CachePolicy policy() {
    return new CachePolicy(
        Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofSeconds(3), 0);
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.EPOCH;

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }
}
