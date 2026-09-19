# Ordinary attack sheathing

Only 3a, 4a1, 4a2 and 4a3 queue ordinary sheathing; stopping after 1a or 2a does not.
RecoveryAttackAnimation queues basic_sheathe only on natural completion of those
four attacks, after a four-tick input grace period. Held attack input refreshes the grace period even when the current attack cannot
yet be interrupted. A subsequent action cancels the queue, so chained attacks, skills and
interruptions take priority. Walking, airborne state, item use and hurt/paralysis
prevent the cosmetic recovery; movement or item use interrupts it after it starts.

basic_sheathe is a separate ActionAnimation containing frames 71–105 of
sheathe_flourish, rebased to zero (34/60 seconds). It has a 0.10-second entry blend,
no root movement, no attack damage and no attack/skill locks. The clipped section
retains the turn, insertion and final settling poses. Blade trails use the shared
renderer, with their window shifted by 71 frames; the settle flash is shifted too.

It never calls CombatController.sheatheEnded and never enters Phase.SHEATHE or
writes specialUntil. The original spin-slash sheathe and its empowered-4a1 window
remain separate. Ordinary sheathing does not refresh or grant that window.

Validation: python tools/validate_assets.py verifies every clipped pose and rebased
timestamp. gradlew.bat build verifies API compatibility and packages the animation.
In-game checks remain necessary: release after each basic attack, hold/queue the
next attack, move/block/jump or take damage, then attack after ordinary sheathing
(no newly granted 4a1 buff) and after spin sheathing (original buff retained).

Combo priority fix: basic_sheathe explicitly preserves the existing EpicFight combo
counter in the weapon capability callback. It cannot reset 2a back to 1a.
The two-tick held-input heartbeat is recorded before canBasicAttack checks;
ordinary sheathing waits until no such request has arrived for more than four ticks.
