#!/usr/bin/env python3
"""Writes the «Licencias» list from what the app actually ships (author's decision, 2026-10-13).

What ships is read from the same files the dependency gate reads — never from what was declared:
  · Android: androidApp/gradle.lockfile, configuration `releaseRuntimeClasspath` (composeApp rides in it);
  · iOS, Kotlin side: composeApp/gradle.lockfile, `iosArm64CompileKlibraries`;
  · iOS, Swift side: the workspace's Package.resolved.
Each group is named through config/licenses.txt, and a shipped group without a line there FAILS: nobody ships a
dependency whose licence was never written down. Our own kit (LumeUIComposer) is a composite build, not a locked
dependency, and is the author's: it is not a third party and is not listed.

  Scripts/generate-licenses.py           rewrites composeApp/src/commonMain/composeResources/files/licenses.tsv
  Scripts/generate-licenses.py --check   exits non-zero when that file is not what the lockfiles say (CI)
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAPPING = ROOT / "config/licenses.txt"
OUTPUT = ROOT / "composeApp/src/commonMain/composeResources/files/licenses.tsv"
LOCKS = [
    (ROOT / "androidApp/gradle.lockfile", "releaseRuntimeClasspath"),
    (ROOT / "composeApp/gradle.lockfile", "iosArm64CompileKlibraries"),
]
RESOLVED = ROOT / "iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved"


def mapping():
    rows = []
    for line in MAPPING.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        prefix, project, licence = (part.strip() for part in line.split("|"))
        rows.append((prefix, project, licence))
    return rows


def shipped():
    groups = set()
    for path, configuration in LOCKS:
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.startswith("#") or "=" not in line:
                continue
            coordinate, configurations = line.split("=", 1)
            if configuration in configurations.split(","):
                groups.add(coordinate.split(":")[0])
    for pin in json.loads(RESOLVED.read_text(encoding="utf-8"))["pins"]:
        groups.add("spm:" + pin["identity"])
    if not groups:
        sys.exit("FAIL no shipped dependency found — the lockfiles or their configurations moved")
    return groups


def owner(group, rows):
    # A prefix matches the whole group or a dot-separated parent of it, never a substring of a segment.
    candidates = [r for r in rows if group == r[0] or group.startswith(r[0] + ".")]
    return max(candidates, key=lambda r: len(r[0])) if candidates else None


def render():
    rows = mapping()
    projects, unknown = set(), []
    for group in sorted(shipped()):
        row = owner(group, rows)
        if row is None:
            unknown.append(group)
        else:
            projects.add((row[1], row[2]))
    if unknown:
        sys.exit("FAIL shipped without a licence line in config/licenses.txt: " + ", ".join(unknown))
    lines = [f"{project}\t{licence}" for project, licence in sorted(projects, key=lambda p: p[0].lower())]
    return "\n".join(lines) + "\n"


def main():
    text = render()
    if "--check" in sys.argv[1:]:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != text:
            sys.exit(f"FAIL {OUTPUT.relative_to(ROOT)} is stale: run Scripts/generate-licenses.py")
        print("OK licences match what ships")
        return
    OUTPUT.write_text(text, encoding="utf-8")
    print(f"wrote {OUTPUT.relative_to(ROOT)} ({text.count(chr(10))} projects)")


if __name__ == "__main__":
    main()
