"""Regenerate the desktop icons from the two SVG masters with librsvg."""
from pathlib import Path
import shutil
import struct
import subprocess


ROOT = Path(__file__).resolve().parents[1]
SIZES = (16, 24, 32, 48, 64, 128, 256)


def rasterize(svg, size):
    return subprocess.check_output([
        "rsvg-convert", "--width", str(size), "--height", str(size), str(svg),
    ])


def write_windows_icon(svg):
    # Vista and later support lossless PNG frames inside ICO directories.
    frames = [rasterize(svg, size) for size in SIZES]
    directory = bytearray(struct.pack("<HHH", 0, 1, len(frames)))
    offset = 6 + 16 * len(frames)
    for size, frame in zip(SIZES, frames):
        dimension = size % 256  # Zero denotes 256 pixels in an ICO entry.
        directory.extend(struct.pack("<BBBBHHII", dimension, dimension, 0, 0,
                                     1, 32, len(frame), offset))
        offset += len(frame)
    svg.with_suffix(".ico").write_bytes(directory + b"".join(frames))


def main():
    for app in ("hub", "editor"):
        images = ROOT / f"modules/{app}/src/main/resources/com/jvn/{app}/images"
        svg = images / f"jvn_{app}_icon.svg"
        svg.with_suffix(".png").write_bytes(rasterize(svg, 1024))
        write_windows_icon(svg)
        if app == "hub":
            # Preserve the public documentation URL for the Hub artwork.
            shutil.copyfile(svg, ROOT / "docs/assets/images/jvn_app_icon.svg")


if __name__ == "__main__":
    main()
