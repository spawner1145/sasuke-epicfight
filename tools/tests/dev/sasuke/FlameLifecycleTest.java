package dev.sasuke;
public final class FlameLifecycleTest {
    public static void main(String[] args) {
        var flame = new FlameLifecycle(100);
        check(!flame.survivesInterrupt(100), "Cancel within three-frame windup");
        check(flame.survivesInterrupt(101), "After three frames, projectile survives interruption");
        check(!flame.ready(121), "No stage two before first explosion");
        flame.exploded = true;
        check(!flame.ready(120) && flame.ready(121), "Original second-stage window start");
        check(flame.survivesInterrupt(130) && flame.ready(130), "Recovery from stun retains second stage");
        check(flame.ready(180) && !flame.ready(181), "Interruption must not extend window");
        flame.consumed = true;
        check(!flame.ready(140), "Canceled stage two cannot be cast again");
        check(flame.survivesInterrupt(140), "Canceling motion never reverses a completed first explosion");
        System.out.println("PASS: first windup, detached lifetime, second-stage retention/expiry/consumption");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
