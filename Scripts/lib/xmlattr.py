#!/usr/bin/env python3
"""Read an attribute off a named element of an XML file, by PARSING it.

Why a parser and not a grep: a grep answers "does this text appear in the file?", and four
manifest attributes were moved inside a four-line XML comment — the file stayed well-formed, the
`<application>` element lost all four, and two gates stayed green. A parser cannot see a comment,
so that whole class of bait is impossible by construction rather than by a filter someone has to
keep ahead of.

It also closes a second hole the grep never covered: the RIGHT ELEMENT. `android:allowBackup` on an
`<activity>` is not the app's backup posture, but it is the same text.

Usage: xmlattr.py FILE ELEMENT ATTRIBUTE
  Prints the attribute value and exits 0 when the element exists and carries it.
  Exits 1 (silently) when the element or the attribute is absent — the caller writes the message.
  Exits 2 on a malformed or missing file, which is never a pass.
"""
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: xmlattr.py FILE ELEMENT ATTRIBUTE", file=sys.stderr)
        return 2
    path, element, attribute = sys.argv[1], sys.argv[2], sys.argv[3]
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as error:
        print(f"xmlattr: cannot read {path}: {error}", file=sys.stderr)
        return 2

    # ElementTree expands `android:foo` to `{uri}foo`; accept the prefixed spelling for convenience.
    if attribute.startswith("android:"):
        attribute = "{%s}%s" % (ANDROID_NS, attribute[len("android:") :])

    candidates = [root] if root.tag == element else []
    candidates += list(root.iter(element))
    for node in candidates:
        if attribute in node.attrib:
            print(node.attrib[attribute])
            return 0
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
