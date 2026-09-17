from pathlib import Path
import argparse
from PIL import Image, ImageOps

project = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description="将指定图片转换为武器物品图标")
parser.add_argument("source", type=Path, help="原始 JPG 或 PNG 图片路径")
source = parser.parse_args().source
destination = project / "src/main/resources/assets/sasuke_epicfight/textures/item/kusanagi_icon.png"
with Image.open(source) as image:
    artwork = ImageOps.contain(ImageOps.exif_transpose(image).convert("RGBA"), (256, 256), Image.Resampling.LANCZOS)
    icon = Image.new("RGBA", (256, 256))
    icon.alpha_composite(artwork, ((256 - artwork.width) // 2, (256 - artwork.height) // 2))
    icon.save(destination)
