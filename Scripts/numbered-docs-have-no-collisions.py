#!/usr/bin/env python3
"""Every numbered document namespace holds each number exactly once.

Three namespaces here name their files `NNNN-slug.md` and are addressed by that number in
prose: the ADRs, the requests to the backend, and the bitacora.

**The bitacora index is deliberately NOT checked.** `docs/bitacora/README.md` declares itself
abandoned at entry 0001 and says the real index is the directory listing; rebuilding or deleting
that table is the author's decision, recorded there. Checking it would report a decision as a
defect. When the author rebuilds it, the reconciliation block from the LumeMed sibling drops
straight in.

Nothing in this repo READS those trees, so a collision is invisible to the compiler, to the linter
and to every test: it is the "una regla sin gate se cae sola" failure mode committed on
documentation. The number IS the address — the constitution and the author both say "ADR 0029" and
expect one file to answer.

This gate is a sibling of `LumeMed/Scripts/numbered-docs-have-no-collisions.py` and they are kept
recognisably the same on purpose; only the namespace table below is per-repo. The original was
written on 2026-09-21 after a cherry-pick from a parallel session landed seven tasks and one
bitacora on numbers the branch had already used, with the whole pipeline green. Both sessions had
numbered correctly against the tree in front of them — the collision is born when the branches
join, which is why this runs in CI and not in anyone's discipline while writing.

Checks:
  1. No number appears twice inside a namespace.
  2. Every numbered-looking `.md` in the repo lives under a declared root and is named canonically.


Each namespace declares a floor, because a gate that scans zero passes green — which is worse than
not having one.

**The floor only guards DOWNWARD, and that was the original's worst blind spot.** It fires when a
declared root shrinks; it can never fire when a numbered file appears where nobody walks, because
an undeclared directory contributes zero to every count and the declared counts sit exactly where
they were. Measured in LumeMed: a third file claiming task `0011`, one level above the two walked
folders, passed green and defeated the exact-path pinning that exists to stop a third copy. Hence
check 2, which sweeps the other way. In a young repo that sweep is the most valuable half: it is
precisely while a repo is still opening new namespaces that one gets opened where nobody looks.

What it cannot see, and is manual: whether a file's own `# NNNN` heading matches its filename,
whether prose citing a number cites the right one, and the same namespaces in the sibling repos.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

# (namespace, roots to walk, floor). The floor is a "did this look at the right tree" guard, not a
# target: these namespaces only grow.
NAMESPACES = [
    ("docs/adr", ["docs/adr"], 25),
    ("docs/backend-requests", ["docs/backend-requests"], 5),
    ("docs/bitacora", ["docs/bitacora"], 25),
    # ONE namespace over two folders: a task MOVES from PENDING to DONE when it closes, so a number
    # must be unique across both or the move itself creates the collision (added 2026-09-24).
    ("tareas", ["tareas/PENDING", "tareas/DONE"], 10),
]

# Canonical shape. `NNNN-slug.md`.
NUMBERED = re.compile(r"^(\d{4})-.+\.md$")
# Looser on purpose: check 2 has to SEE a near-miss name in order to reject it.
NUMBERISH = re.compile(r"^\d{3,4}")
# Build output and parallel sessions' worktrees hold whole copies of these trees; walking them
# would report every file in them as a collision.
SKIP_DIRS = {".git", ".build", "build", "DerivedData", "node_modules", "Pods", ".swiftpm",
             ".claude", ".gradle", ".idea", "out"}


def collect(roots):
    found = {}
    for root in roots:
        for path in sorted((ROOT / root).rglob("*.md")):
            match = NUMBERED.match(path.name)
            if match:
                found.setdefault(match.group(1), []).append(path.relative_to(ROOT))
    return found


def main() -> int:
    problems = []

    for namespace, roots, floor in NAMESPACES:
        found = collect(roots)
        if len(found) < floor:
            problems.append(
                f"{namespace}: found {len(found)} numbered files, floor is {floor} — "
                f"the gate is looking at the wrong tree, not at a clean one"
            )
            continue
        for number, paths in sorted(found.items()):
            if len(paths) > 1:
                listed = ", ".join(str(p) for p in paths)
                problems.append(f"{namespace}: {number} is claimed by {len(paths)} files — {listed}")

    declared_roots = {ROOT / root for _, roots, _ in NAMESPACES for root in roots}
    for path in sorted(ROOT.rglob("*.md")):
        if not NUMBERISH.match(path.name) or SKIP_DIRS & {p.name for p in path.parents}:
            continue
        relative = path.relative_to(ROOT)
        if not any(root in path.parents for root in declared_roots):
            problems.append(
                f"{relative} is numbered but sits outside every declared namespace — "
                f"nothing checks it for collisions"
            )
        elif not NUMBERED.match(path.name):
            problems.append(
                f"{relative} starts with a number but is not named canonically — "
                f"the number cannot be addressed"
            )

    if problems:
        print("error: a numbered document namespace has a collision")
        for problem in problems:
            print(f"  {problem}")
        return 1

    counts = ", ".join(f"{name} {len(collect(roots))}" for name, roots, _ in NAMESPACES)
    print(f"ok: no collisions ({counts})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
