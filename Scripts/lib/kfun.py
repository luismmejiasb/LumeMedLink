#!/usr/bin/env python3
"""Print what one Kotlin function runs, with comments and string literals blanked, on one line.

Why this exists: a gate that asserts a CALL must assert it where the call has effect. A presence check
over a whole file is satisfied by the same call parked in a helper nobody invokes — the third of the
three places a token can live without being the mechanism, after comments and string literals
(ADR-0029: the mechanism, in the place where it runs). This narrows "the file" to "the function".

What "what it runs" means, by the shape of the declaration:
  · block body       `fun f(...) { ... }`            -> the braces' content
  · lambda body      `fun f(...) = Builder.create { ... }` -> the lambda's content, prefixed by the
                     text between `=` and the brace, so a gate can also see WHAT is being created
  · expression body  `fun f(...) = other()`          -> the expression, up to the next declaration

Parameters are skipped by matching parentheses, so a default value that is itself a lambda
(`addition: Config.() -> Unit = {}`) is not mistaken for the body.

Usage: kfun.py FILE NAME     (exit 1 when the file or the function is missing)
"""
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from uncomment import blank_c  # noqa: E402  (sibling module, path set above)

DECLARATION = re.compile(r"\s(?:@|(?:internal|private|public|protected|actual|expect|override|fun|val|var|class|object|interface)\b)")


def flatten(text: str) -> str:
    return " ".join(text.split())


def match_close(code: str, start: int, open_ch: str, close_ch: str) -> int:
    depth = 0
    for i in range(start, len(code)):
        if code[i] == open_ch:
            depth += 1
        elif code[i] == close_ch:
            depth -= 1
            if depth == 0:
                return i
    return -1


def body_of(code: str, name: str):
    # `fun name(` or `fun Receiver.name(` — the receiver may carry dots and generics.
    m = re.search(r"\bfun\s+(?:[\w.<>?,\s]+\.)?" + re.escape(name) + r"\s*\(", code)
    if m is None:
        return None
    params_end = match_close(code, m.end() - 1, "(", ")")
    if params_end == -1:
        return None
    rest = code[params_end + 1:]
    brace, equals = rest.find("{"), rest.find("=")
    if brace == -1 and equals == -1:
        return None
    if equals == -1 or (brace != -1 and brace < equals):
        end = match_close(rest, brace, "{", "}")
        return None if end == -1 else rest[brace + 1:end]
    expr = rest[equals + 1:]
    nxt = DECLARATION.search(expr)
    lam = expr.find("{")
    if lam != -1 and (nxt is None or lam < nxt.start()):
        end = match_close(expr, lam, "{", "}")
        return None if end == -1 else expr[:lam] + "{" + expr[lam + 1:end] + "}"
    return expr if nxt is None else expr[: nxt.start()]


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    path, name = pathlib.Path(sys.argv[1]), sys.argv[2]
    if not path.is_file():
        print(f"kfun: no such file: {path}", file=sys.stderr)
        return 1
    code = flatten(blank_c(path.read_text(encoding="utf-8", errors="replace"), strip_strings=True))
    body = body_of(code, name)
    if body is None:
        print(f"kfun: no function '{name}' in {path}", file=sys.stderr)
        return 1
    print(flatten(body))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
