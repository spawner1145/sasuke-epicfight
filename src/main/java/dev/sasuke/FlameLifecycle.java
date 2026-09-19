package dev.sasuke;

/** Server-tick lifetime independent of the caster's animation state. */
final class FlameLifecycle {
    final long began;
    boolean exploded;
    boolean consumed;
    FlameLifecycle(long began) { this.began = began; }
    boolean launched(long now) { return now >= began + 1; }
    boolean expired(long now) { return now >= began + 81; }
    boolean ready(long now) { return exploded && !consumed && now >= began + 21 && !expired(now); }
    boolean survivesInterrupt(long now) { return launched(now); }
}
