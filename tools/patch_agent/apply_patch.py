import sys
import subprocess
import shutil
from pathlib import Path


def run(cmd, cwd=None, check=True):
    """Run a command, echo it, capture stdout/stderr, and optionally raise on error."""
    print(">>", " ".join(cmd))
    p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if p.stdout:
        # Avoid double newlines; subprocess already includes them
        print(p.stdout, end="")
    if p.stderr:
        # Keep stderr on stderr stream
        print(p.stderr, file=sys.stderr, end="")
    if check and p.returncode != 0:
        raise RuntimeError(f"Command failed: {' '.join(cmd)}")
    return p


def get_repo_root(start: Path) -> Path:
    """Resolve the Git toplevel from anywhere under the repo."""
    try:
        p = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            cwd=start, capture_output=True, text=True, check=True
        )
        return Path(p.stdout.strip())
    except Exception:
        # Fallback: walk up until .git is found
        cur = start
        for _ in range(6):
            if (cur / ".git").exists():
                return cur
            if cur.parent == cur:
                break
            cur = cur.parent
        return start


def is_mailbox_patch(path: Path) -> bool:
    """Detect 'git format-patch' (mbox) files that must be applied with git am."""
    try:
        with open(path, "rb") as f:
            first = f.readline()
        return first.startswith(b"From ")
    except Exception:
        return False


def has_remote(repo: Path, name: str) -> bool:
    """Return True if a Git remote with the given name exists."""
    try:
        p = subprocess.run(
            ["git", "remote"],
            cwd=repo, capture_output=True, text=True, check=True
        )
        names = [r.strip() for r in (p.stdout or "").splitlines()]
        return name in names
    except Exception:
        return False


def current_branch(repo: Path) -> str:
    """Return current branch name, defaulting to 'main' if undetectable."""
    try:
        p = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            cwd=repo, capture_output=True, text=True, check=True
        )
        b = (p.stdout or "").strip()
        return b if b else "main"
    except Exception:
        return "main"


def push_if_remote(repo: Path, remote: str = "public") -> None:
    """If the given remote exists, push the current HEAD to remote/<branch>."""
    if not has_remote(repo, remote):
        print(f"[PUSH] Remote '{remote}' not found; skipping auto-push.")
        return
    branch = current_branch(repo)
    print(f"[PUSH] Pushing HEAD to {remote}/{branch} ...")
    p = subprocess.run(
        ["git", "push", remote, f"HEAD:{branch}"],
        cwd=repo, capture_output=True, text=True
    )
    if p.stdout:
        print(p.stdout, end="")
    if p.stderr:
        print(p.stderr, file=sys.stderr, end="")
    if p.returncode == 0:
        print(f"[PUSH] Push to {remote} completed.")
    else:
        print(f"[PUSH] Push to {remote} failed with code {p.returncode}.")


def unique_target(path: Path) -> Path:
    """Return a non-clobbering path like 'name (1).ext' if needed."""
    if not path.exists():
        return path
    base = path.stem
    ext = path.suffix
    i = 1
    while (path.parent / f"{base} ({i}){ext}").exists():
        i += 1
    return path.parent / f"{base} ({i}){ext}"


def apply_one(repo: Path, patch_path: Path) -> bool:
    """Apply one patch file. Supports mailbox (git am) and raw diffs (git apply)."""
    try:
        if is_mailbox_patch(patch_path):
            # mailbox patch -> use git am; creates the commit(s)
            run(["git", "am", "--3way", str(patch_path)], cwd=repo)
        else:
            # raw diff -> apply to index then commit a single snapshot
            run(["git", "apply", "--index", "--reject", "--whitespace=nowarn", str(patch_path)], cwd=repo)
            # Commit directly with filename (without extension) as message
            run(["git", "commit", "-m", patch_path.stem], cwd=repo)
        print(f"[OK] Applied patch: {patch_path.name}")
        return True
    except Exception as e:
        print(f"[FAIL] Could not apply patch: {e}")
        # If git am left the repo mid-apply, abort to clean state
        try:
            run(["git", "am", "--abort"], cwd=repo, check=False)
        except Exception:
            pass
        return False


def main():
    if len(sys.argv) < 2:
        print("Usage: apply_patch.py <patch-file>")
        sys.exit(2)

    here = Path(__file__).resolve().parent
    repo = get_repo_root(here)

    patches_dir = repo / "patches"
    incoming = patches_dir / "incoming"
    applied = patches_dir / "applied"
    failed = patches_dir / "failed"

    patch_file = Path(sys.argv[1]).resolve()

    ok = apply_one(repo, patch_file)
    dest_dir = applied if ok else failed
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = unique_target(dest_dir / patch_file.name)
    shutil.move(str(patch_file), str(dest))

    if ok:
        push_if_remote(repo, "public")

    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
