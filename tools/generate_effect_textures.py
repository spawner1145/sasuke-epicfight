from pathlib import Path
import math
from PIL import Image

destination = Path(__file__).resolve().parents[1] / "src/main/resources/assets/sasuke_epicfight/textures/particle"
destination.mkdir(parents=True, exist_ok=True)
for kind in ("flame", "spark", "ring"):
    image = Image.new("RGBA", (128, 128))
    pixels = image.load()
    for row in range(128):
        for column in range(128):
            horizontal = (column + 0.5) / 64 - 1
            vertical = (row + 0.5) / 64 - 1
            radius = math.hypot(horizontal, vertical)
            if kind == "ring":
                angle = math.atan2(vertical, horizontal)
                edge = 0.77 + 0.012 * math.sin(angle * 13)
                alpha = math.exp(-((radius - edge) / 0.025) ** 2) * 0.8
                alpha += math.exp(-((radius - edge) / 0.075) ** 2) * 0.2
            elif kind == "spark":
                alpha = math.exp(-horizontal ** 2 * 85 - vertical ** 2 * 7) * max(0, 1 - radius)
            else:
                height = (1 - vertical) * 0.5
                width = max(0.025, 0.5 * (1 - height) ** 0.65)
                bend = 0.13 * math.sin(height * 10) * height
                core = math.exp(-((horizontal - bend) / width) ** 2 * 3)
                tongues = 0.65 + 0.35 * math.sin(horizontal * 28 + height * 18) ** 2
                alpha = core * tongues * math.sin(math.pi * height) ** 0.65
            pixels[column, row] = (255, 255, 255, round(255 * min(1, max(0, alpha))))
    image.save(destination / f"{kind}.png")
print("Generated flame, spark and pressure-ring textures")
