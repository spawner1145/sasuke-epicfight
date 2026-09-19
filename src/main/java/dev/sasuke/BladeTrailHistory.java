package dev.sasuke;

import java.util.ArrayList;
import java.util.List;

/** Timestamped, immutable world-space samples; never reproject old samples. */
final class BladeTrailHistory<T> {
    record Sample<T>(double tick, T edge) { }

    private final List<Sample<T>> samples = new ArrayList<>();

    void add(double tick, T edge) {
        if (!samples.isEmpty() && tick <= samples.get(samples.size() - 1).tick()) return;
        samples.add(new Sample<>(tick, edge));
        // Bound memory even on very high refresh-rate clients.
        if (samples.size() > 256) samples.remove(0);
    }

    void trim(double cutoff) {
        // Retain one edge before the cutoff so the tail does not pop each frame.
        while (samples.size() > 1 && samples.get(1).tick() < cutoff) samples.remove(0);
    }

    List<Sample<T>> samples() { return samples; }
}
