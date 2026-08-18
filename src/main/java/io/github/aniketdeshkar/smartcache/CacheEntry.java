package io.github.aniketdeshkar.smartcache;

import java.time.Instant;
import java.util.Optional;

public record CacheEntry<V>(Optional<V> value, Instant freshUntil, Instant staleUntil) {
  public boolean fresh(Instant now) {
    return now.isBefore(freshUntil);
  }

  public boolean staleUsable(Instant now) {
    return !fresh(now) && now.isBefore(staleUntil);
  }
}
