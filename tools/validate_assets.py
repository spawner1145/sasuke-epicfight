import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/sasuke_epicfight/animmodels'
manifest = json.loads((ROOT / 'asset-manifest.json').read_text(encoding='utf-8'))
spirit = json.loads((ASSETS / 'entity/susanoo.json').read_text())
player_names = set(json.loads((ASSETS / 'animations/player/idle.json').read_text())['animation'][index]['name'] for index in range(20))
attack_ends = {'1a': 22, '2a': 31, '3a': 22, '4a1': 36, '4a2': 31, '4a3': 51}

for folder, joints in [('player', player_names), ('susanoo', set(spirit['armature']['joints']))]:
    for name, info in manifest['actions'].items():
        data = json.loads((ASSETS / f'animations/{folder}/{name}.json').read_text())
        tracks = data['animation']
        assert set(track['name'] for track in tracks) == joints, (folder, name, 'joint mismatch')
        for track in tracks:
            times = track['time']
            assert len(times) == len(track['transform']) > 1
            assert all(before < after for before, after in zip(times, times[1:]))
            expected = attack_ends[name] / 60 if folder == 'player' and name in attack_ends else info['seconds']
            assert abs(times[-1] - expected) < 0.002, (folder, name, times[-1])
            assert all(len(matrix) == 16 and all(math.isfinite(value) for value in matrix) for matrix in track['transform'])

for relative, joint_count in [('entity/susanoo.json', len(spirit['armature']['joints'])), ('item/kusanagi.json', len(player_names))]:
    vertices = json.loads((ASSETS / relative).read_text())['vertices']
    positions = vertices['positions']['array']
    counts = vertices['vcounts']['array']
    weights = vertices['weights']['array']
    indices = vertices['vindices']['array']
    assert len(counts) == len(positions) // 3
    assert sum(counts) * 2 == len(indices)
    assert all(0 <= index < joint_count for index in indices[::2])
    assert all(0 <= index < len(weights) for index in indices[1::2])
    offset = 0
    for count in counts:
        total = sum(weights[indices[offset + index * 2 + 1]] for index in range(count))
        assert abs(total - 1) < 0.002, (relative, 'unnormalized vertex', total)
        offset += count * 2
    limits = [len(positions) // 3, len(vertices['uvs']['array']) // 2, len(vertices['normals']['array']) // 3]
    for part in vertices['parts'].values():
        assert len(part['array']) % 9 == 0
        assert all(0 <= value < limits[index % 3] for index, value in enumerate(part['array']))

print(f'PASS: {len(manifest["actions"]) * 2} baked animations, both skinned meshes, weights and indices')
