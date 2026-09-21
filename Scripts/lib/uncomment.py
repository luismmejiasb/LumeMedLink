#!/usr/bin/env python3
"""Print source with comments — and optionally string literals — blanked out.

Why this exists, and why it is a tokenizer rather than a `grep -v`:

A gate that asserts a control is PRESENT answers the question "does this token appear?". Three
things in a file can carry a token without being the mechanism: a comment, a string literal, and a
file that never ships. This tool removes the first two; the caller restricts the path to remove the
third.

The line-based filters this replaces failed on all of these, measured:
  · an XML comment spanning four lines carries its `<!--` only on the first, so every line after
    the first passed `grep -v '<!--'` and four manifest attributes went missing with the gate green;
  · a Kotlin block comment's inner lines start with neither `//` nor `*`;
  · `val doc = "window.decorView.denyAutofillExport()"` is a string, not a call.

Blanking rather than deleting: line and column counts survive, so `grep -n` on the output still
points at the real line of the real file. That is what keeps a gate's failure message useful.

String literals are only blanked with --strip-strings, and only for presence checks. An ABSENCE
check must keep them: a forbidden URL or a forbidden permission name is often exactly a string.

Usage: uncomment.py --lang {xml,c} [--strip-strings] [--flatten] FILE...
  --lang c        C-style comments: // to end of line, /* */ NESTED (Kotlin and Swift both nest).
  --flatten       collapse all whitespace to single spaces and emit one line, so a gate can match a
                  call that the formatter wrapped across lines. Loses line numbers by design.
"""
import argparse
import pathlib
import sys


def blank_xml(text: str) -> str:
    out, i, n = [], 0, len(text)
    while i < n:
        if text.startswith("<!--", i):
            end = text.find("-->", i + 4)
            end = n if end == -1 else end + 3
            # Keep the newlines so line numbers do not move.
            out.append("".join(c if c == "\n" else " " for c in text[i:end]))
            i = end
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def blank_c(text: str, strip_strings: bool) -> str:
    out, i, n, depth = [], 0, len(text), 0

    def keep_shape(chunk: str) -> str:
        return "".join(c if c == "\n" else " " for c in chunk)

    while i < n:
        if depth:
            if text.startswith("/*", i):
                depth += 1
                out.append("  ")
                i += 2
            elif text.startswith("*/", i):
                depth -= 1
                out.append("  ")
                i += 2
            else:
                out.append(text[i] if text[i] == "\n" else " ")
                i += 1
            continue
        if text.startswith("//", i):
            end = text.find("\n", i)
            end = n if end == -1 else end
            out.append(keep_shape(text[i:end]))
            i = end
        elif text.startswith("/*", i):
            depth = 1
            out.append("  ")
            i += 2
        elif text.startswith('"""', i):
            end = text.find('"""', i + 3)
            end = n if end == -1 else end + 3
            out.append(keep_shape(text[i:end]) if strip_strings else text[i:end])
            i = end
        elif text[i] in ('"', "'"):
            quote, j = text[i], i + 1
            while j < n and text[j] != quote:
                if text[j] == "\\":
                    j += 1
                if text[j : j + 1] == "\n":
                    break
                j += 1
            end = min(j + 1, n)
            out.append(keep_shape(text[i:end]) if strip_strings else text[i:end])
            i = end
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", choices=("xml", "c"), required=True)
    ap.add_argument("--strip-strings", action="store_true")
    ap.add_argument("--flatten", action="store_true")
    ap.add_argument("files", nargs="+")
    args = ap.parse_args()

    for name in args.files:
        path = pathlib.Path(name)
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        code = blank_xml(text) if args.lang == "xml" else blank_c(text, args.strip_strings)
        if args.flatten:
            code = " ".join(code.split())
            sys.stdout.write(code + "\n")
        else:
            sys.stdout.write(code if code.endswith("\n") else code + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
