#!/bin/sh
# Every form step is a LumeForm (ADR-0054, constitution §5, task 0033).
#
# LumeContainer's action slot rises with the keyboard and its content does not chain Next field to field; LumeForm
# pins its footer under the keyboard and chains the fields. So a field drawn inside a LumeContainer is a form step
# built on the wrong frame, and this gate refuses it.
#
# The mechanism, where it runs (ADR-0029):
#   · comments and string literals are blanked by the tokenizer (Scripts/lib/uncomment.py), never filtered by line;
#   · "inside" is the call's own argument span — its parentheses and its trailing lambda, matched by depth;
#   · both ends are TRANSITIVE by name. A field reached through a helper (`NewPasswordFields`) is a field, and a
#     container reached through a wrapper (`PushedScreen`) is a container: a wrapper is any function of the app whose
#     body calls a container and that takes composable content to hand it, a holder any function whose body calls a
#     field. Both are closed to a fixpoint.
#
# The fields are core/input's text inputs. SensitiveSearchField is not one of them: a search bar filters a list and
# submits nothing, so it is not a form step. A sheet is not a container either: its fields are checked by eye.
#
# Usage: Scripts/check-form-steps.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

python3 - <<'EOF'
import pathlib
import re
import sys

sys.path.insert(0, "Scripts/lib")
from uncomment import blank_c

ROOT = pathlib.Path("composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink")
FEATURES = ROOT / "features"
FIELDS = {"SensitiveTextField", "SensitivePhoneField", "SensitiveWordsField", "SensitiveWordsArea", "OneTimeCodeField"}
CONTAINERS = {"LumeContainer"}

sources = {p: blank_c(p.read_text(), True) for p in sorted(ROOT.rglob("*.kt"))}
if not any(str(p).startswith(str(FEATURES)) for p in sources):
    print("form-steps: FAIL — scanned no feature file; the path moved and this gate went blind")
    sys.exit(1)


def matching(text, i, open_c, close_c):
    depth = 0
    for j in range(i, len(text)):
        if text[j] == open_c:
            depth += 1
        elif text[j] == close_c:
            depth -= 1
            if depth == 0:
                return j
    return len(text) - 1


def call_span(text, start):
    """From a call's `(`, its arguments plus a trailing lambda, if one follows."""
    end = matching(text, start, "(", ")")
    k = end + 1
    while k < len(text) and text[k] in " \t\n":
        k += 1
    if k < len(text) and text[k] == "{":
        end = matching(text, k, "{", "}")
    return start, end


def calls(span, names):
    return [m for m in re.finditer(r"\b([A-Z][A-Za-z0-9]*)\s*(?:\(|\{)", span) if m.group(1) in names]


FUN = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?([A-Z][A-Za-z0-9]*)\s*\(")
bodies = {}
takes_content = set()
for path, text in sources.items():
    for m in FUN.finditer(text):
        params_end = matching(text, m.end() - 1, "(", ")")
        if "@Composable" in text[m.end():params_end]:
            takes_content.add(m.group(1))
        brace = text.find("{", params_end)
        eq = text.find("=", params_end)
        if brace == -1:
            continue
        if eq != -1 and eq < brace:
            # Expression body: up to the end of its first call and that call's trailing lambda.
            paren = text.find("(", eq)
            if paren == -1 or paren > brace:
                _, end = brace, matching(text, brace, "{", "}")
            else:
                _, end = call_span(text, paren)
            bodies.setdefault(m.group(1), []).append(text[eq:end + 1])
        else:
            bodies.setdefault(m.group(1), []).append(text[brace:matching(text, brace, "{", "}") + 1])

holders, wrappers = set(FIELDS), set(CONTAINERS)
changed = True
while changed:
    changed = False
    for name, spans in bodies.items():
        if name not in holders and name not in FIELDS and any(calls(s, holders) for s in spans):
            holders.add(name)
            changed = True
        if name not in wrappers and name in takes_content and any(calls(s, wrappers) for s in spans):
            wrappers.add(name)
            changed = True

hits = []
for path, text in sources.items():
    if not str(path).startswith(str(FEATURES)):
        continue
    for m in re.finditer(r"\b([A-Z][A-Za-z0-9]*)\s*\(", text):
        if m.group(1) not in wrappers or text[max(0, m.start() - 4):m.start()].strip().endswith("fun"):
            continue
        start, end = call_span(text, m.end() - 1)
        inside = calls(text[start:end + 1], holders)
        if inside:
            line = text.count("\n", 0, m.start()) + 1
            names = ", ".join(sorted({c.group(1) for c in inside}))
            hits.append(f"{path}:{line}: {m.group(1)} holds {names}")

if hits:
    print("")
    print("FAIL form-steps: a field inside a LumeContainer")
    print("  Every form step is a LumeForm: its footer stays pinned under the keyboard and its fields chain Next (ADR-0054).")
    for h in hits:
        print("    " + h)
    sys.exit(1)
print(f"form-steps: OK ({len(wrappers)} containers, {len(holders)} field holders)")
EOF
