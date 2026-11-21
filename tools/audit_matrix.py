"""Count push/pop per method and flag the unbalanced ones.

GL_STACK_OVERFLOW (1283) means more pushes than pops. The legacy MODELVIEW stack is
32 deep, so one unpaired push per frame overflows in about half a second.

This is a COARSE check: it counts statically, so it cannot see an exception path that
skips a pop. Treat a pass as "no obvious imbalance", not proof. The real guard is the
depth counter inside the mod's Draw helper, which unwinds at runtime; this script only
catches the dumb case early, before a build is shipped.

Usage:  python3 audit_matrix.py <jar> [class ...]
"""
import subprocess
import sys
import zipfile

PUSH = ("GlStateManager.pushMatrix", "GL11.glPushMatrix", "Draw.push")
POP = ("GlStateManager.popMatrix", "GL11.glPopMatrix", "Draw.pop")


def audit(jar, cls):
    out = subprocess.run(["javap", "-c", "-p", "-cp", jar, cls.replace("/", ".")],
                         capture_output=True, text=True, timeout=90)
    if out.returncode != 0:
        return ["%s: unreadable" % cls]

    problems, current, body = [], None, []

    def flush():
        if not current or not body:
            return
        p = sum(1 for l in body if any(k in l for k in PUSH))
        q = sum(1 for l in body if any(k in l for k in POP))
        if p != q:
            problems.append("%s.%s: %d push vs %d pop" % (cls, current, p, q))

    for line in out.stdout.split("\n"):
        s = line.strip()
        if s.endswith(");") and "(" in s and not s.startswith("//"):
            flush()
            current, body = s.split("(", 1)[0], []
            continue
        body.append(line)
    flush()
    return problems


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    jar = sys.argv[1]
    classes = sys.argv[2:]
    if not classes:
        with zipfile.ZipFile(jar) as z:
            classes = [n[:-6] for n in z.namelist() if n.endswith(".class")]

    all_problems = []
    for c in sorted(classes):
        all_problems.extend(audit(jar, c))

    if all_problems:
        print("  FAIL %d unbalanced method(s):" % len(all_problems))
        for p in all_problems:
            print("    %s" % p)
        return 1
    print("  PASS %d classes, every push statically paired with a pop" % len(classes))
    return 0


if __name__ == "__main__":
    sys.exit(main())
