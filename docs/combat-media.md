# Combat audio and combo movie

Run `python tools/import_media.py` to convert ../audios and ../videos using
imageio-ffmpeg. Runtime does not require FFmpeg. The supplied 0.6-second movie has
35 video frames: these are stored at 1280x684, retaining 60 fps. Its original audio track is omitted.

Audio counters are per player and persistent. 4a3 cycles 1/2/3; Susanoo and spin
cycle 1/2. Each stage-one Amaterasu selects pair 1/2/3; stage two reuses that pair.
Stage-two speech waits six ticks for the Susanoo chord. Both chord orders resolve
within six ticks and use only Susanoo speech. Interrupted stage two cancels speech.
Normal attack and sheathe clips start on the server animation action event.
Lightning explosions use the supplied clip, replacing the generic explosion sound.

Successful initial grab sends a CG packet only to the caster and a player victim;
non-player victims have no client. Empty grabs never send it. Both packets contain
the same server start tick (animation frame 35 at 60 fps, plus its 0.08-second entry blend), to align playback despite packet arrival
order. Frames are preloaded on joining a level. The screen covers by center-cropping,
is non-pausing, consumes gameplay input and closes after 0.6 seconds. The supplied combo-hit sound starts locally on the same render as the first movie
frame for both viewers, without a duplicate server sound or movie soundtrack.
The voice finishes naturally even after the short movie closes.
Network protocol is now 7; clients and server must use this build together.

The planted sword still expires at 200 ticks (10 seconds). Expiry/removal/dimension
change resets a pending 4a3 cycle. Before selecting the next basic attack, a second
anchor check maps the current 1/2/3/4 position back into the 4a1 group. A valid
4a3 consumes the sword through the existing lightning projectile flow.

Validation: gradlew.bat build, python tools/validate_assets.py, and
python tools/validate_media.py. Multiplayer synchronization, audible sequences and
actual fullscreen playback still require an in-game two-client check.

All imported source clips receive 2x gain (+6.02 dB) before Vorbis encoding,
with a 0.85 peak limiter to avoid clipping. CG is scheduled without opening or
blocking the screen before its start time; its grab voice starts with the video.

Sound-source correction: all imported voices originate at the caster, including
combo_hit scheduled at frame 35. Nearby players hear that positional sound; only
caster and victim see the movie. Lightning burst originates at the actual strike
position. Network protocol 8 supersedes the earlier packet format.
