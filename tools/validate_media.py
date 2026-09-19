import json, subprocess, zipfile
from pathlib import Path
from PIL import Image
import imageio_ffmpeg
root=Path(__file__).resolve().parents[1]
a=root/'src/main/resources/assets/sasuke_epicfight'
sounds=json.loads((a/'sounds.json').read_text())
assert len(sounds)==23
assert "combo_cg" not in sounds
assert not (a/"sounds/combo_cg.ogg").exists()
for entry in sounds.values():
 for sound in entry['sounds']:
  ns,name=sound['name'].split(':');p=a/'sounds'/f'{name}.ogg'
  assert p.read_bytes().startswith(b'OggS')
  subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-v','error','-i',str(p),'-f','null','-'],check=True,capture_output=True)
for i in range(35):
 with Image.open(a/'textures/cg'/f'combo_{i:03}.png') as image:
  assert image.size==(1280,684);image.verify()
with zipfile.ZipFile(root/'build/libs/sasuke-epicfight-0.1.0.jar') as jar:
 assert not any(n.endswith("combo_cg.ogg") for n in jar.namelist())
 for p in [a/'sounds.json',*(a/'sounds').glob('*.ogg'),*(a/'textures/cg').glob('*.png')]:
  name=p.relative_to(root/'src/main/resources').as_posix()
  assert jar.read(name)==p.read_bytes(),name
print('PASS: 23 decodable Vorbis sounds, 35 valid movie frames, identical packaged media')
