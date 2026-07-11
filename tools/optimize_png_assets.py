from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path

from PIL import Image, __version__ as pillow_version


PINNED_PILLOW_VERSION = "12.0.0"
ASSET_DIRS = (
    "icons",
    "background",
    "alisa",
    "anan",
    "coco",
    "ema",
    "hanna",
    "hiro",
    "mago",
    "meruru",
    "miria",
    "nanoka",
    "noa",
    "reia",
    "sherri",
    "yuki",
)


def decoded_signature(path: Path) -> tuple[tuple[int, int], str]:
    with Image.open(path) as image:
        rgba = image.convert("RGBA")
        return rgba.size, hashlib.sha256(rgba.tobytes()).hexdigest()


def optimize(path: Path) -> dict[str, object]:
    original_size = path.stat().st_size
    original_dimensions, original_hash = decoded_signature(path)
    temporary = path.with_suffix(path.suffix + ".opt.tmp")
    try:
        with Image.open(path) as image:
            image.load()
            image.save(temporary, format="PNG", optimize=True, compress_level=9)
        optimized_dimensions, optimized_hash = decoded_signature(temporary)
        if optimized_dimensions != original_dimensions or optimized_hash != original_hash:
            raise RuntimeError(f"decoded pixels changed: {path}")
        optimized_size = temporary.stat().st_size
        replaced = optimized_size < original_size
        if replaced:
            os.replace(temporary, path)
        return {
            "path": path.as_posix(),
            "before": original_size,
            "after": optimized_size if replaced else original_size,
            "saved": original_size - optimized_size if replaced else 0,
            "replaced": replaced,
            "rgbaSha256": original_hash,
            "width": original_dimensions[0],
            "height": original_dimensions[1],
        }
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Losslessly optimize and verify bundled PNG assets.")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    if pillow_version != PINNED_PILLOW_VERSION:
        raise RuntimeError(
            f"Pillow {PINNED_PILLOW_VERSION} is required; found {pillow_version}."
        )

    root = args.root.resolve()
    files = sorted(
        path
        for directory in ASSET_DIRS
        for path in (root / directory).glob("**/*.png")
        if path.is_file()
    )
    results = [optimize(path) for path in files]
    report = {
        "tool": f"Pillow {pillow_version}",
        "mode": "PNG optimize=True, compress_level=9",
        "pixelCheck": "dimensions + decoded RGBA SHA-256",
        "files": len(results),
        "optimizedFiles": sum(1 for item in results if item["replaced"]),
        "bytesBefore": sum(int(item["before"]) for item in results),
        "bytesAfter": sum(int(item["after"]) for item in results),
        "bytesSaved": sum(int(item["saved"]) for item in results),
        "items": results,
    }
    report_path = args.report or root / "android_apk" / "png-optimization-report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: report[key] for key in report if key != "items"}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
