# Continuous blade trails

The blade uses EpicFight 20.14.17's `AnimationTrailParticle` through animation
`data/*.json` files. DevilMineCraft 1.0.4's `BloomTrailParticle` delegates its
geometry to this same class; its separate bloom pipeline is not required here.
No DevilMineCraft classes or textures are bundled or required.

EpicFight retains world-space blade-root and blade-tip positions from the owner
animator and connects successive samples with cubic Bezier interpolation. This
includes player movement and animation blending. Each tick uses 12 interpolation
steps per half-tick and keeps 3–4 ticks of history. End-time and fade-time settings
stop emission before recovery and let the existing trail disappear.

`Tool_R` endpoints are (-0.005, -0.0177, 0.0898) and (-0.005, 0.1474, -1.70).
The texture maps U from old to new history, V=0 to the blade tip and V=1 to the
blade root. Its white outer core and cyan inner falloff follow `refer/1a.png`.
It remains opaque at the leading U edge so the trail connects to the blade;
the tapered alpha footprint is not a pre-painted arc or spiral.

Trails are configured for 1a, 3a, 4a1, dash_spin_slash and sheathe_flourish.
2a retains only kick air pressure. Thrust lightning, spin dust/black embers and
sheathe glints remain separate effects. The old sampled fan and rotating spin
fan are no longer drawn. DMC's flowing vertex distortion is intentionally not
used because it would displace the surface away from the actual blade path.

Validation: `python tools/validate_assets.py` and `gradlew.bat build`.
Check in game at both low and high frame rates: moving 1a, chained attacks,
spin while translating, an interrupted attack, 2a without a blade trail,
and sheathing. Build/resource checks do not establish visual fidelity.
