from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'src/main/resources/assets/sasuke_epicfight/textures/particle'
DESTINATION.mkdir(parents=True, exist_ok=True)
SIZE = 512
rows, columns = np.mgrid[:SIZE, :SIZE].astype(float)
horizontal = (columns + 0.5 - SIZE / 2) / (SIZE * 0.47)
vertical = (rows + 0.5 - SIZE / 2) / (SIZE * 0.47)
radius = np.hypot(horizontal, vertical)
angle = np.arctan2(vertical, horizontal)


def save(name, red, green, blue, alpha):
    rgba = np.stack(np.broadcast_arrays(red, green, blue, np.clip(alpha, 0, 1) * 255), axis=-1)
    Image.fromarray(np.uint8(np.clip(rgba, 0, 255))).save(DESTINATION / f'{name}.png')


def smooth(lower, upper, value):
    fraction = np.clip((value - lower) / (upper - lower), 0, 1)
    return fraction * fraction * (3 - 2 * fraction)


progress = np.clip((angle + 2.45) / 4.9, 0, 1)
width = 0.015 + 0.43 * np.sin(progress * math.pi / 2) ** 1.15
inside = 1 - width
sector = smooth(inside, inside + 0.065, radius) * (1 - smooth(0.975, 1.005, radius))
sector *= smooth(0, 0.055, progress) * (1 - smooth(0.975, 1, progress))
sector *= (np.abs(angle) < 2.45)
rim = smooth(0.84, 0.96, radius)
striations = 0.86 + 0.14 * np.sin(radius * 175 + angle * 3) ** 2
save('slash_fan', 78 + 177 * rim, 226 + 29 * rim, 255, sector * striations)
save('black_fan', 12 + 223 * rim ** 8, 3 + 204 * rim ** 8, 26 + 229 * rim ** 8,
     sector * (0.9 + 0.1 * striations))
trail_x = columns / (SIZE - 1)
trail_y = rows / (SIZE - 1)
trail_width = 0.04 + 0.72 * trail_x ** 0.8
trail_body = 1 - smooth(trail_width * 0.24, trail_width, trail_y)
trail_core = 1 - smooth(0.035, 0.13, trail_y)
trail_alpha = trail_body * smooth(0, 0.018, trail_y)
trail_alpha *= smooth(0, 0.09, trail_x)
save('slash_trail', 85 + 170 * trail_core, 218 + 37 * trail_core, 255, trail_alpha)
wind_edge = 0.91 + 0.018 * np.sin(angle * 19) + 0.012 * np.sin(angle * 31)
wind = np.exp(-((radius - wind_edge) / 0.023) ** 2)
wind += 0.18 * np.exp(-((radius - 0.85) / 0.075) ** 2)
wind *= smooth(0, 0.08, progress) * (1 - smooth(0.85, 1, progress)) * (np.abs(angle) < 2.45)
save('kick_air', 238, 247, 255, wind)
save('ring', 255, 255, 255, np.exp(-((radius - 0.84) / 0.017) ** 2)
     + 0.18 * np.exp(-((radius - 0.84) / 0.055) ** 2))
save('halo', 255, 255, 255, np.exp(-radius ** 2 * 5.5) * (1 - smooth(0.65, 1, radius)))
save('spark', 255, 255, 255, np.exp(-horizontal ** 2 * 210 - vertical ** 2 * 5)
     * (1 - smooth(0.65, 1, radius)))
star = np.exp(-radius ** 2 * 70)
star += np.exp(-np.abs(horizontal) * 85 - np.abs(vertical) * 3.5)
star += np.exp(-np.abs(vertical) * 100 - np.abs(horizontal) * 3.5)
save('glint', 255, 255, 255, star * (1 - smooth(0.78, 1, radius)))
dust = np.exp(-radius ** 2 * 4) * (0.65 + 0.17 * np.sin(horizontal * 19 + vertical * 9)
       + 0.18 * np.cos(vertical * 21 - horizontal * 4)) * (1 - smooth(0.7, 1, radius))
save('dust', 255, 255, 255, dust * 0.6)
Image.new('RGBA', (4, 4), (255, 255, 255, 255)).save(DESTINATION / 'white.png')


def ribbon(draw, points, widths, color):
    left = []
    right = []
    for index, point in enumerate(points):
        before = points[max(0, index - 1)]
        after = points[min(len(points) - 1, index + 1)]
        delta = np.array(after) - before
        normal = np.array([-delta[1], delta[0]]) / max(0.0001, np.linalg.norm(delta))
        left.append(tuple(np.array(point) + normal * widths[index]))
        right.append(tuple(np.array(point) - normal * widths[index]))
    draw.polygon(left + right[::-1], fill=color)


def flame_frame(frame, ink):
    image = Image.new('RGBA', (256, 256))
    draw = ImageDraw.Draw(image)
    phase = frame * math.tau / 8
    for strand in range(5):
        points = []
        widths = []
        height = 165 + 40 * math.sin(strand * 1.7 + 0.8)
        for sample in range(65):
            fraction = sample / 64
            bend = math.sin(fraction * 5.5 + phase + strand) * 26 * fraction
            curl = math.sin(fraction * 10 + phase) * 12 * fraction ** 3
            points.append((128 + (strand - 2) * 24 * (1 - fraction) + bend + curl,
                           238 - height * fraction))
            widths.append((22 - strand * 1.8) * (1 - fraction) ** 0.85 + 0.2)
        ribbon(draw, points, widths, (8, 2, 17, 255) if ink else (255, 255, 255, 255))
        if ink:
            ribbon(draw, [(point[0] + 3, point[1] - 1) for point in points],
                   [width * 0.26 for width in widths], (54, 9, 87, 220))
    alpha = np.array(image.getchannel('A')).astype(float)
    alpha *= (1 - smooth(225, 254, np.arange(256)[:, None]))
    image.putalpha(Image.fromarray(alpha.astype('uint8')))
    return image.filter(ImageFilter.GaussianBlur(0.45))


atlas = Image.new('RGBA', (2048, 256))
for frame in range(8):
    atlas.paste(flame_frame(frame, True), (frame * 256, 0))
atlas.save(DESTINATION / 'black_flame.png')
flame_frame(0, False).save(DESTINATION / 'flame.png')
curl_image = Image.new('RGBA', (256, 256))
curl_draw = ImageDraw.Draw(curl_image)
curl_points = []
curl_widths = []
for sample in range(121):
    fraction = sample / 120
    rotation = -0.7 + fraction * math.pi * 2.05
    distance = 90 * (1 - fraction) + 12
    curl_points.append((128 + math.cos(rotation) * distance, 128 + math.sin(rotation) * distance))
    curl_widths.append(24 * math.sin(math.pi * fraction) ** 0.75 + 0.1)
ribbon(curl_draw, curl_points, curl_widths, (8, 2, 16, 255))
ribbon(curl_draw, [(point[0] - 2, point[1] - 3) for point in curl_points],
       [width * 0.23 for width in curl_widths], (65, 8, 98, 255))
curl_image.filter(ImageFilter.GaussianBlur(0.45)).save(DESTINATION / 'flame_curl.png')

preview = Image.new('RGB', (1024, 768), '#344751')
preview_draw = ImageDraw.Draw(preview)
names = ['slash_fan', 'slash_trail', 'black_fan', 'kick_air', 'glint', 'black_flame', 'flame_curl', 'ring']
for index, name in enumerate(names):
    tile = Image.open(DESTINATION / f'{name}.png')
    if name == 'black_flame':
        tile = tile.crop((0, 0, 256, 256))
    tile = tile.resize((240, 300), Image.Resampling.LANCZOS)
    left = index % 4 * 256 + 8
    top = index // 4 * 384 + 24
    preview.paste(tile, (left, top), tile)
    preview_draw.text((left, top + 315), name, fill='white')
(ROOT / 'build').mkdir(exist_ok=True)
preview.save(ROOT / 'build/effect-texture-preview.png')
print(f'Generated {len(list(DESTINATION.glob("*.png")))} effect textures and preview')
