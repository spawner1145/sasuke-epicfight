package dev.sasuke;

/** Run with javac/java; no Minecraft client or test framework required. */
public final class BladeTrailHistoryTest {
    private record Edge(double root, double tip) { }

    public static void main(String[] args) {
        var trail = new BladeTrailHistory<Edge>();
        Edge beforeMoving = new Edge(10, 12);
        trail.add(0, beforeMoving);
        trail.add(0.5, new Edge(20, 22));
        trail.add(1, new Edge(30, 32));
        check(trail.samples().get(0).edge().equals(beforeMoving), "Movement must not reproject history");
        trail.add(1, new Edge(99, 99));
        trail.add(0.9, new Edge(99, 99));
        check(trail.samples().size() == 3, "Repeated/paused frames cannot add duplicate edges");
        trail.trim(0.75);
        check(trail.samples().size() == 2 && trail.samples().get(0).tick() == 0.5,
            "Retain the edge straddling the time-based tail boundary");
        for (int i = 2; i < 2000; i++) trail.add(i, new Edge(i, i + 2));
        check(trail.samples().size() == 256, "High refresh rates must not grow history indefinitely");
        check(trail.samples().get(255).tick() == 1999, "Never discard the current blade edge");
        check(new BladeTrailHistory<Edge>().samples().isEmpty(), "A new attack cannot inherit old geometry");
        System.out.println("PASS: world-space history, paused frames, tail trimming and bounded storage");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
