#!/usr/bin/env python3
"""Generate Google Play listing graphics from existing project assets."""

from __future__ import annotations

import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "play-store"
PANEL = ROOT / "docs" / "screenshots" / "sensevoice-ime-panel.png"
LAUNCHER_BG = ROOT / "app/src/main/res/drawable/ic_launcher_background.xml"
LAUNCHER_FG = ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml"

BG = "#F8FAFD"
ACCENT = "#0B57D0"
TEXT = "#1F1F1F"
SUBTEXT = "#444746"


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/PingFang.ttc",
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            return ImageFont.truetype(str(path), size=size)
    return ImageFont.load_default()


def draw_centered_text(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    text: str,
    font: ImageFont.ImageFont,
    fill: str,
) -> None:
    bbox = draw.multiline_textbbox((0, 0), text, font=font, spacing=8, align="center")
    width = bbox[2] - bbox[0]
    height = bbox[3] - bbox[1]
    x = box[0] + (box[2] - box[0] - width) // 2
    y = box[1] + (box[3] - box[1] - height) // 2
    draw.multiline_text((x, y), text, font=font, fill=fill, spacing=8, align="center")


def android_vector_to_svg(vector_xml: Path) -> str:
    root = ET.parse(vector_xml).getroot()
    viewport_width = root.attrib.get("{http://schemas.android.com/apk/res/android}viewportWidth", "108")
    viewport_height = root.attrib.get("{http://schemas.android.com/apk/res/android}viewportHeight", "108")
    paths: list[str] = []
    android_ns = "{http://schemas.android.com/apk/res/android}"
    for path in root.findall("path"):
        attrs = path.attrib
        path_data = attrs.get(f"{android_ns}pathData")
        if not path_data:
            continue
        svg_attrs: list[str] = [f'd="{path_data}"']
        fill = attrs.get(f"{android_ns}fillColor")
        stroke = attrs.get(f"{android_ns}strokeColor")
        stroke_width = attrs.get(f"{android_ns}strokeWidth")
        stroke_linecap = attrs.get(f"{android_ns}strokeLineCap")
        if fill:
            svg_attrs.append(f'fill="{fill}"')
        elif stroke:
            svg_attrs.append('fill="none"')
        if stroke:
            svg_attrs.append(f'stroke="{stroke}"')
        if stroke_width:
            svg_attrs.append(f'stroke-width="{stroke_width}"')
        if stroke_linecap:
            svg_attrs.append(f'stroke-linecap="{stroke_linecap}"')
        paths.append(f'  <path {" ".join(svg_attrs)} />')
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {viewport_width} {viewport_height}">\n'
        + "\n".join(paths)
        + "\n</svg>\n"
    )


def rasterize_svg(svg: str, size: int) -> Image.Image:
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as handle:
        handle.write(svg)
        svg_path = Path(handle.name)
    png_path = svg_path.with_suffix(".png")
    try:
        subprocess.run(
            ["rsvg-convert", "-w", str(size), "-h", str(size), str(svg_path), "-o", str(png_path)],
            check=True,
        )
        return Image.open(png_path).convert("RGBA")
    finally:
        svg_path.unlink(missing_ok=True)
        png_path.unlink(missing_ok=True)


def render_launcher_icon(size: int) -> Image.Image:
    background = rasterize_svg(android_vector_to_svg(LAUNCHER_BG), size)
    foreground = rasterize_svg(android_vector_to_svg(LAUNCHER_FG), size)
    icon = Image.new("RGBA", (size, size))
    icon.alpha_composite(background)
    icon.alpha_composite(foreground)
    return icon


def make_feature_graphic() -> None:
    image = Image.new("RGB", (1024, 500), BG)
    icon = render_launcher_icon(164)
    image.paste(icon, (56, 72), icon)
    draw = ImageDraw.Draw(image)
    draw.text((248, 118), "Fcitx5 SenseVoice", font=load_font(54, bold=True), fill=TEXT)
    draw.text(
        (248, 196),
        "纯离线中文语音输入法 · 本地 SenseVoice 识别",
        font=load_font(30),
        fill=SUBTEXT,
    )
    draw.text(
        (248, 252),
        "无需联网 · 不上传录音 · 与 Fcitx5 配合使用",
        font=load_font(24),
        fill=SUBTEXT,
    )
    image.save(OUT / "feature-graphic.png")


def make_icon() -> None:
    render_launcher_icon(512).save(OUT / "icon-512.png")


def framed_screenshot(title: str, subtitle: str, output_name: str) -> None:
    panel = Image.open(PANEL).convert("RGB")
    canvas = Image.new("RGB", (1080, 1920), BG)
    draw = ImageDraw.Draw(canvas)
    draw.text((72, 96), title, font=load_font(52, bold=True), fill=TEXT)
    draw.text((72, 176), subtitle, font=load_font(30), fill=SUBTEXT)
    target_width = 936
    scale = target_width / panel.width
    target_height = int(panel.height * scale)
    resized = panel.resize((target_width, target_height), Image.Resampling.LANCZOS)
    x = (canvas.width - target_width) // 2
    y = 280
    canvas.paste(resized, (x, y))
    draw.rounded_rectangle(
        (x - 8, y - 8, x + target_width + 8, y + target_height + 8),
        radius=28,
        outline="#D3E3FD",
        width=4,
    )
    canvas.save(OUT / "phone" / output_name)


def make_phone_screenshots() -> None:
    phone_dir = OUT / "phone"
    phone_dir.mkdir(parents=True, exist_ok=True)
    framed_screenshot("语音输入面板", "点击或长按麦克风开始离线识别", "01-voice-panel.png")
    framed_screenshot("分段提交", "停顿后自动识别并插入文字", "02-segment-commit.png")
    framed_screenshot("纯离线运行", "不申请网络权限，录音仅在本地处理", "03-offline-privacy.png")
    framed_screenshot("Fcitx5 集成", "通过 Fcitx5 语音按钮切换到本输入法", "04-fcitx5-integration.png")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    if not LAUNCHER_BG.exists() or not LAUNCHER_FG.exists():
        raise SystemExit("Missing launcher vector drawables in app/src/main/res/drawable/")
    if not PANEL.exists():
        raise SystemExit(f"Missing screenshot source: {PANEL}")
    make_feature_graphic()
    make_icon()
    make_phone_screenshots()
    print(f"Generated Play Store assets in {OUT}")


if __name__ == "__main__":
    main()
