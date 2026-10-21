#!/usr/bin/env python3
"""
Generates the Gameyfin plugin marketplace manifests (plugins/<plugin-id>.json) for a release.

For every plugin variant, the template in marketplace/<plugin-id>.json is combined with the releases already
published in the marketplace (if --existing points to a checkout of the marketplace's plugins/ directory) and a
new release entry for the given version, including the SHA-512 checksum of the built JAR.

Usage:
  python3 scripts/marketplace_manifests.py --version 1.0.2 --jars build/libs --out build/marketplace \
      [--existing ../plugin-marketplace/plugins] [--date 2026-10-21] [--changelog "..."]
"""

from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import zipfile
from pathlib import Path

REPO_URL = "https://github.com/mdmatthias/gameyfin-gog-metadata-plugin"
TEMPLATES_DIR = Path(__file__).resolve().parent.parent / "marketplace"

# Plugin ID -> JAR base name (see build.gradle.kts)
VARIANTS = {
    "io.github.mdmatthias.gameyfin.plugins.gog": "gog-plugin",
    "io.github.mdmatthias.gameyfin.plugins.gog-catalog": "gog-plugin-catalog",
    "io.github.mdmatthias.gameyfin.plugins.gog-dreamlist": "gog-plugin-dreamlist",
}


def read_jar_manifest(jar: Path) -> dict[str, str]:
    with zipfile.ZipFile(jar) as archive:
        text = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
    attributes: dict[str, str] = {}
    last_key = None
    for line in text.replace("\r\n", "\n").split("\n"):
        if not line:
            break
        if line.startswith(" ") and last_key:
            attributes[last_key] += line[1:]
        elif ":" in line:
            last_key, value = line.split(":", 1)
            attributes[last_key] = value.strip()
    return attributes


def sha512_of(path: Path) -> str:
    digest = hashlib.sha512()
    with path.open("rb") as file:
        while chunk := file.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--version", required=True)
    parser.add_argument("--jars", type=Path, default=Path("build/libs"))
    parser.add_argument("--out", type=Path, default=Path("build/marketplace"))
    parser.add_argument("--existing", type=Path, help="plugins/ directory of the marketplace repository")
    parser.add_argument("--date", default=datetime.date.today().isoformat())
    parser.add_argument("--changelog")
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    for plugin_id, jar_name in VARIANTS.items():
        file_name = f"{jar_name}-{args.version}.jar"
        jar = args.jars / file_name
        attributes = read_jar_manifest(jar)
        if attributes.get("Plugin-Id") != plugin_id or attributes.get("Plugin-Version") != args.version:
            raise SystemExit(f"{jar}: Plugin-Id/Plugin-Version don't match {plugin_id} {args.version}")
        if not attributes.get("Plugin-Requires"):
            raise SystemExit(f"{jar}: Plugin-Requires is missing")

        release = {
            "version": args.version,
            "date": args.date,
            "requires": attributes["Plugin-Requires"],
            "url": f"{REPO_URL}/releases/download/v{args.version}/{file_name}",
            "sha512sum": sha512_of(jar),
        }
        if args.changelog:
            release["changelog"] = args.changelog

        manifest = json.loads((TEMPLATES_DIR / f"{plugin_id}.json").read_text(encoding="utf-8"))
        releases = []
        existing = args.existing / f"{plugin_id}.json" if args.existing else None
        if existing and existing.exists():
            releases = json.loads(existing.read_text(encoding="utf-8")).get("releases", [])
            # Published releases are immutable, never overwrite one
            if any(r["version"] == args.version for r in releases):
                raise SystemExit(f"{existing}: version {args.version} is already published")
        manifest["releases"] = [release, *releases]

        target = args.out / f"{plugin_id}.json"
        target.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"Wrote {target}")


if __name__ == "__main__":
    main()
