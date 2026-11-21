#!/usr/bin/env python3
"""Fail the build when a method invokes itself.

Why this exists: a wrapper named after the call it makes —

    private static void push() { push(); depth++; }

— compiles cleanly, throws no NoSuchMethodError, and PASSES a per-method push/pop
balance audit (the audit counts one push and one pop; the recursive invokestatic *is*
the push). At runtime it is a StackOverflowError inside the draw callback, which the
per-feature `catch (Throwable)` swallows, so the feature looks permanently absent and
the log shows only that it threw.

Checks every method body in every class of the jar for `invokestatic`/`invokevirtual`/
`invokespecial`/`invokeinterface` whose target is `<same class>.<same method>`.

Usage:  python3 check_selfrecursion.py <mod.jar>
        Requires `javap` on PATH (a JDK 8 bin directory is fine).

Exit status: 0 = clean, 1 = recursion found, 2 = checked nothing (treated as a failure,
because a verifier that examined zero methods has proven nothing).
"""

from __future__ import annotations

import re
import subprocess
import sys
import zipfile

METHOD_START = re.compile(
    r"^  (?:public |private |protected |static |final |abstract |native |synchronized )*"
    r"[\w.$<>\[\]]+ ([\w$<>]+)\(",
    re.M,
)
INVOKE = re.compile(
    r"invoke\w+\s+#\d+\s+//\s+(?:Interface)?Method (?P<owner>[\w/$]+)\."
    r"(?P<method>[\w$<>]+):"
)


def class_names(jar: str) -> list[str]:
    with zipfile.ZipFile(jar) as zf:
        return [n[:-6].replace("/", ".") for n in zf.namelist() if n.endswith(".class")]


def javap(jar: str, cls: str) -> str:
    proc = subprocess.run(
        ["javap", "-p", "-c", "-cp", jar, cls], capture_output=True, text=True
    )
    return proc.stdout


def audit(jar: str) -> tuple[list[str], int]:
    problems: list[str] = []
    checked = 0
    for cls in class_names(jar):
        source = javap(jar, cls)
        if not source:
            continue
        starts = [(m.start(), m.group(1)) for m in METHOD_START.finditer(source)]
        bounds = [s for s, _ in starts] + [len(source)]
        for idx, (start, method) in enumerate(starts):
            body = source[start : bounds[idx + 1]]
            checked += 1
            for call in INVOKE.finditer(body):
                owner = call.group("owner").replace("/", ".")
                if owner == cls and call.group("method") == method:
                    problems.append(f"{cls}.{method} invokes itself")
                    break
    return problems, checked


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    problems, checked = audit(sys.argv[1])
    if checked == 0:
        print("  FAIL checked 0 methods — the audit proved nothing")
        return 2
    if problems:
        print(f"  FAIL {len(problems)} self-recursive method(s) across {checked} checked")
        for p in problems:
            print(f"    {p}")
        return 1
    print(f"  PASS {checked} methods, no method invokes itself")
    return 0


if __name__ == "__main__":
    sys.exit(main())
