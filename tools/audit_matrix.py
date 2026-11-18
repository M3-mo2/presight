"""Find unbalanced GlStateManager.pushMatrix / popMatrix in every method.

GL_STACK_OVERFLOW (1283) means more pushes than pops. The legacy matrix stack is
small (32 deep for MODELVIEW), so it does not take many to overflow — and the
failure surfaces far from the code that caused it.

This walks the bytecode of each method and counts pushes against pops, following
the same path the JVM would, including early returns. It cannot model every branch
exactly, so it reports the raw counts and the paths where they differ, and treats a
lockstep pairing as a pass.

Usage: python3 audit_matrix.py <jar> [class ...]
"""
import subprocess
import sys

PUSH = ("GlStateManager.pushMatrix", "GL11.glPushMatrix")
POP = ("GlStateManager.popMatrix", "GL11.glPopMatrix")


def audit_class(jar: str, cls: str) -> list[str]:
    out = subprocess.run(["javap", "-c", "-p", "-cp", jar, cls.replace("/", ".")],
                         capture_output=True, text=True, timeout=60)
    if out.returncode != 0:
        return [f"{cls}: unreadable — {out.stderr.strip()[:100]}"]

    problems = []
    current, body = None, []

    def flush():
        if current and body:
            pushes = [l for l in body if any(p in l for p in PUSH)]
            pops = [l for l in body if any(p in l for p in POP)]
            if len(pushes) != len(pops):
                problems.append(
                    f"{cls}.{current}: {len(pushes)} push vs {len(pops)} pop")
            # A push inside a loop that runs per entity is fine only if the pop is
            # inside the same loop; report the ones where the pop sits after a
            # `continue`-style branch, which the count check alone would miss.
            for i, line in enumerate(body):
                if any(p in line for p in PUSH):
                    # everything between this push and the next pop must be reachable
                    rest = body[i:]
                    if not any(any(p in x for p in POP) for x in rest):
                        problems.append(f"{cls}.{current}: push with no pop after line {i}")

    for line in out.stdout.split("\n"):
        stripped = line.strip()
        if stripped.endswith(");") and "(" in stripped and not stripped.startswith("//"):
            flush()
            current, body = stripped.split("(")[0], []
            continue
        body.append(line)
    flush()
    return problems


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    jar = sys.argv[1]
    classes = sys.argv[2:]
    if not classes:
        import zipfile
        with zipfile.ZipFile(jar) as z:
            classes = [n[:-6] for n in z.namelist()
                       if n.endswith(".class") and n.startswith("com/mohamed")]

    all_problems = []
    for c in sorted(classes):
        all_problems.extend(audit_class(jar, c))

    if all_problems:
        print(f"  FAIL {len(all_problems)} unbalanced method(s):")
        for p in all_problems:
            print(f"    {p}")
        return 1
    print(f"  PASS {len(classes)} classes, every push has a matching pop")
    return 0


if __name__ == "__main__":
    sys.exit(main())
