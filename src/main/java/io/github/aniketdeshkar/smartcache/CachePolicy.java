package io.github.aniketdeshkar.smartcache;

import java.time.Duration;

public record CachePolicy(
    Duration ttl, Duration staleWhileRevalidate, Duration negativeTtl, double jitterFraction) {
  public CachePolicy {
    if (ttl.isZero()
        || ttl.isNegative()
        || staleWhileRevalidate.isNegative()
        || negativeTtl.isZero()
        || negativeTtl.isNegative()
        || jitterFraction < 0
        || jitterFraction > 1) throw new IllegalArgumentException("invalid cache policy");
  }
}
