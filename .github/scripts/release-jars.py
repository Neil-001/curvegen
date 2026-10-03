#!/usr/bin/env python3
"""Collect release jars and print the publishing matrix. Run from the source checkout."""

import json
from pathlib import Path
import shutil
import sys
import tomllib
from zipfile import ZipFile


def collect(version, line, java, root=Path(".")):
    dual_loader = (root / "fabric").is_dir() and (root / "neoforge").is_dir()
    patterns = ("fabric/build/libs/*.jar", "neoforge/build/libs/*.jar") if dual_loader else (
        "build/libs/*.jar", "versions/*/build/libs/*.jar"
    )
    jars = []
    sources = {}
    for pattern in patterns:
        for jar in sorted(root.glob(pattern)):
            if jar.name.endswith(("-dev.jar", "-sources.jar", "-javadoc.jar")):
                continue
            # Stonecutter can also copy a version's jar into the root build/libs directory.
            if jar.name in sources:
                if jar.read_bytes() != sources[jar.name].read_bytes():
                    raise ValueError(f"Different jars have the same filename: {jar.name}")
                continue
            with ZipFile(jar) as archive:
                if "fabric.mod.json" in archive.namelist():
                    metadata = json.loads(archive.read("fabric.mod.json"))
                    loader = "fabric"
                    jar_version = metadata["version"]
                    minecraft = metadata["depends"]["minecraft"]
                    if isinstance(minecraft, list):
                        minecraft = "\n".join(minecraft)
                else:
                    metadata = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode())
                    mod = next(mod for mod in metadata["mods"] if mod["modId"] == "curvegen")
                    loader = "neoforge"
                    jar_version = mod["version"]
                    minecraft = next(dep["versionRange"] for dep in metadata["dependencies"]["curvegen"]
                                     if dep["modId"] == "minecraft")
            if jar_version != version and not jar_version.startswith(version + "+"):
                raise ValueError(f"{jar} has version {jar_version}, expected {version}")
            if dual_loader and loader != jar.relative_to(root).parts[0]:
                raise ValueError(f"{jar} has metadata for the wrong loader: {loader}")
            published_version = f"{version}+mc{line}-{loader}" if dual_loader else jar_version
            if any(item["version"] == published_version for item in jars):
                raise ValueError(f"Multiple jars would publish as {published_version}")
            jars.append(dict(file=jar.name, version=published_version, loader=loader,
                             java=java, minecraft=minecraft))
            sources[jar.name] = jar
    if not jars:
        raise ValueError("The build produced no jars")
    if dual_loader and {item["loader"] for item in jars} != {"fabric", "neoforge"}:
        raise ValueError("The build must produce both Fabric and NeoForge jars")
    # Validate everything before copying, so a partial collection cannot be published.
    dist = root / "dist"
    dist.mkdir(exist_ok=True)
    for name, source in sources.items():
        shutil.copyfile(source, dist / name)
    return jars


if __name__ == "__main__":
    try:
        print(json.dumps(collect(*sys.argv[1:]), separators=(",", ":")))
    except (ValueError, KeyError, StopIteration) as error:
        sys.exit(f"::error::{error}")
