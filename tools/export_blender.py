import bpy
import importlib
import json
from pathlib import Path

ROOT = Path(r'D:/code/test model/sasuke-epicfight')
ASSETS = ROOT / 'src/main/resources/assets/sasuke_epicfight'
compat = importlib.import_module('blender-json-addon.compat')
exporter = importlib.import_module('blender-json-addon.export_mc_json')


def plain(value):
    if isinstance(value, compat.NoIndent):
        return plain(value.value)
    if isinstance(value, dict):
        return {key: plain(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [plain(item) for item in value]
    return value


def write(relative, value):
    path = ASSETS / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(plain(value), separators=(',', ':')), encoding='utf-8')


def merge_mesh(names, rig, bones, atlas=False):
    arrays = {key: [] for key in ['positions', 'uvs', 'normals', 'vcounts', 'weights', 'vindices']}
    parts = {}
    for name in names:
        original = bpy.data.objects[name]
        copied = original.copy()
        copied.data = original.data.copy()
        try:
            copied.data.transform(rig.matrix_world.inverted() @ original.matrix_world)
            vertices = plain(exporter.export_mesh(copied, bones, False))
            offsets = [len(arrays['positions']) // 3, len(arrays['uvs']) // 2, len(arrays['normals']) // 3]
            if atlas:
                offset = 0 if name == 'ribcage' else 1
                uv = vertices['uvs']['array']
                for index in range(0, len(uv), 2):
                    uv[index] = (uv[index] + offset) / 2
            indices = vertices['vindices']['array']
            for index in range(1, len(indices), 2):
                indices[index] += len(arrays['weights'])
            for part, data in vertices['parts'].items():
                parts.setdefault(part, []).extend(value + offsets[index % 3] for index, value in enumerate(data['array']))
            for key in arrays:
                arrays[key].extend(vertices[key]['array'])
        finally:
            mesh = copied.data
            bpy.data.objects.remove(copied)
            bpy.data.meshes.remove(mesh)
    result = {key: {'stride': 3 if key in ['positions', 'normals'] else 2 if key == 'uvs' else 1,
                    'count': len(values) // (3 if key in ['positions', 'normals'] else 2 if key == 'uvs' else 1),
                    'array': values} for key, values in arrays.items()}
    result['parts'] = {key: {'stride': 3, 'count': len(values) // 3, 'array': values} for key, values in parts.items()}
    return result


def export_all():
    scene = bpy.context.scene
    frame = scene.frame_current
    saved = []
    for obj in scene.objects:
        if obj.animation_data:
            saved.append((obj, obj.animation_data.action, obj.animation_data.action_slot, obj.animation_data.use_nla))
    manifest = {'source': bpy.data.filepath, 'fps': scene.render.fps, 'actions': {}}
    try:
        for obj, action, slot, use_nla in saved:
            obj.animation_data.use_nla = False
        player = bpy.data.objects['Armature']
        susanoo = bpy.data.objects['Susanoo_Rig']
        player_armature = plain(compat.export_armature(player, False, 'MAT'))
        susanoo_armature = plain(compat.export_armature(susanoo, False, 'MAT'))
        write('animmodels/entity/susanoo.json', {'armature': susanoo_armature, 'vertices': merge_mesh(['ribcage', 'left_arm', 'right_arm'], susanoo, susanoo_armature['joints'], True)})
        write('animmodels/item/kusanagi.json', {'vertices': merge_mesh(['kusanagi', 'scabbard'], player, player_armature['joints'])})
        names = ['1a', '2a', '3a', '4a1', '4a2', '4a3', 'idle', 'idle_guard', 'idle_sword_side', 'run_sheathed', 'run_sword_side', 'draw_to_side', 'draw_to_guard', 'dash_spin_slash', 'sheathe_flourish', 'amaterasu_1', 'amaterasu_2', 'amaterasu_combo', 'perfect_parry']
        for name in names:
            action = bpy.data.actions[name]
            for obj, old_action, old_slot, old_nla in saved:
                slot = next((candidate for candidate in action.slots if candidate.identifier == 'OB' + obj.name), None)
                if slot:
                    obj.animation_data.action = action
                    obj.animation_data.action_slot = slot
            scene.frame_set(0)
            bpy.context.view_layer.update()
            for folder, rig, armature in [('player', player, player_armature), ('susanoo', susanoo, susanoo_armature)]:
                animation = compat.export_animation(rig, armature['joints'], 'MAT', bake=True)
                write('animmodels/animations/' + folder + '/' + name + '.json', {'animation': animation})
            manifest['actions'][name] = {'frames': list(action.frame_range), 'seconds': action.frame_range[1] / scene.render.fps}
        texture_dir = ASSETS / 'textures/entity'
        texture_dir.mkdir(parents=True, exist_ok=True)
        import numpy as np
        pixels = np.zeros((128, 256, 4), dtype=np.float32)
        for column, name in enumerate(['Susanoo_Chakra_Repaint.001', 'Susanoo_Right_Arm_Detail.001']):
            pixels[:, column * 128:(column + 1) * 128, :] = np.array(bpy.data.images[name].pixels[:]).reshape(128, 128, 4)
        atlas = bpy.data.images.new('sasuke_export_atlas', width=256, height=128, alpha=True)
        try:
            atlas.pixels.foreach_set(pixels.ravel())
            atlas.filepath_raw = str(texture_dir / 'susanoo.png')
            atlas.file_format = bpy.data.images['Susanoo_Chakra_Repaint.001'].file_format
            atlas.save()
        finally:
            bpy.data.images.remove(atlas)
        (ROOT / 'asset-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
        print('EXPORTED', len(names), 'player actions and', len(names), 'Susanoo actions')
    finally:
        for obj, action, slot, use_nla in saved:
            obj.animation_data.action = action
            if slot:
                obj.animation_data.action_slot = slot
            obj.animation_data.use_nla = use_nla
        scene.frame_set(frame)
        bpy.context.view_layer.update()


export_all()
