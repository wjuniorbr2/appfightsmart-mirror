import os, sys, subprocess, shutil, re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]  # .../AppFightSmart2
PATCHES = REPO / "patches"
INCOMING = PATCHES / "incoming"
PROCESSED = PATCHES / "processed"
FAILED = PATCHES / "failed"

def run(cmd, cwd=None, check=True):
    # Print command (helps debugging)
    print(">>", " ".join(cmd))
    p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if p.stdout: print(p.stdout)
    if p.stderr: print(p.stderr, file=sys.stderr)
    if check and p.returncode != 0:
        raise RuntimeError(f"Command failed: {' '.join(cmd)}")
    return p

def extract_subject(patch_text: str) -> str:
    # If it looks like a git-format-patch, grab Subject:, else first line
    m = re.search(r"^Subject:\s*(.+)$", patch_text, flags=re.MULTILINE)
    if m:
        return m.group(1).strip()
    # Fallback: first non-empty line
    for line in patch_text.splitlines():
        if line.strip():
            return line.strip()[:72]
    return "Apply patch"

def apply_one(patch_path: Path) -> bool:
    text = patch_path.read_text(encoding="utf-8", errors="ignore")
    subject = extract_subject(text)

    # First try: git am (for email-style patches)
    try:
        run(["git", "am", "--3way", str(patch_path)], cwd=REPO)
        print(f"[OK] Applied with git am: {patch_path.name}")
        return True
    except Exception:
        print("[WARN] git am failed, trying git apply + commit…")

    # Second try: git apply --index (unified diff without email headers)
    try:
        run(["git", "apply", "--index", str(patch_path)], cwd=REPO)
        # Commit with subject
        run(["git", "commit", "-m", subject], cwd=REPO)
        print(f"[OK] Applied with git apply: {patch_path.name}")
        return True
    except Exception as e:
        print(f"[FAIL] Could not apply patch: {e}")
        # Clean any partial am state
        try:
            run(["git", "am", "--abort"], cwd=REPO, check=False)
        except Exception:
            pass
        return False

def main():
    if len(sys.argv) < 2:
        print("Usage: apply_patch.py <patch-file>")
        sys.exit(2)
    patch_file = Path(sys.argv[1]).resolve()
    ok = apply_one(patch_file)
    # Move patch to processed/failed
    target_dir = PROCESSED if ok else FAILED
    target_dir.mkdir(parents=True, exist_ok=True)
    target = target_dir / patch_file.name
    # Avoid overwrite
    if target.exists():
        base = target.stem
        ext = target.suffix
        i = 1
        while (target.parent / f"{base} ({i}){ext}").exists():
            i += 1
        target = target.parent / f"{base} ({i}){ext}"
    shutil.move(str(patch_file), str(target))
    sys.exit(0 if ok else 1)

if __name__ == "__main__":
    main()
