package io.github.aniketdeshkar.smartcache;

public interface CacheMetrics {
  void record(String outcome);

  static CacheMetrics noOp() {
    return outcome -> {};
  }
}
