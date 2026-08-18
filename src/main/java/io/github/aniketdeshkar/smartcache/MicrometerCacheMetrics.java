package io.github.aniketdeshkar.smartcache;

import io.micrometer.core.instrument.MeterRegistry;

public final class MicrometerCacheMetrics implements CacheMetrics {
  private final MeterRegistry registry;

  public MicrometerCacheMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void record(String outcome) {
    registry.counter("smart.cache.requests", "outcome", outcome).increment();
  }
}
