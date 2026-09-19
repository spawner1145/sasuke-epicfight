# Blade trails

`BladeTrails` draws 1a, 3a, 4a1, draw_to_side, dash_spin_slash and sheathe_flourish through
one rendering path. 1a emits throughout its 22/60-second animation, including
its entry blend, rather than only during the old 0.083333–0.215-second window.
3a also emits throughout 22/60 seconds, and 4a1 throughout 36/60 seconds.
Drawing, spinning and sheathing retain their existing emission windows and textures.

Samples use `patch.getAnimator().getPose(partial)` and the same `Tool_R` joint
and model transform as Avalon's `RenderMeshItem`. Twelve subdivisions per tick
follow the blended pose. Blade endpoints are (-0.005, -0.0177, 0.0898) and
(-0.005, 0.1474, -1.70). Samples store their own world positions; moving or turning
never transforms old samples again. World translation stays in double precision
until camera subtraction. The newest edge attaches to the currently rendered blade.

History is time-bounded (0.20 seconds for 1a), with a 256-edge memory cap.
Interrupted/restarted attacks, replaced entities, level changes and players leaving
render range clear state. Link-to-attack transitions retain history. Repeated
renders while paused do not append duplicate edges.

Native `trail_effects` arrays for these custom-rendered actions are empty to prevent
duplicate surfaces. There are no remaining native blade-trail particles or synthetic
thrust light bars. 4a1 retains its independent lightning lance and tip halo, both
anchored to the same rendered-pose blade endpoints; 2a retains kick air pressure only. Spin dust, black embers and sheathe
glints remain separate. No DevilMineCraft runtime is required.

Validation:

```powershell
python tools/validate_assets.py
javac -d build/trail-tests src/main/java/dev/sasuke/BladeTrailHistory.java tools/tests/dev/sasuke/BladeTrailHistoryTest.java
java -cp build/trail-tests dev.sasuke.BladeTrailHistoryTest
./gradlew.bat build
```

In-game checks: stationary/moving/turning 1a, 3a and 4a1 at low and high frame rates, entry
blending, final recovery frames, interrupted/restarted attacks, black-flame spin,
and sheathing. Automated checks do not establish visual fidelity.
