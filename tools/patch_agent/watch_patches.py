import time
from pathlib import Path
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
import subprocess, sys

ROOT = Path(__file__).resolve().parents[2]
INCOMING = ROOT / "patches" / "incoming"
APPLY = Path(__file__).resolve().parent / "apply_patch.py"

def run(cmd, cwd=None):
    print(">>", " ".join(cmd))
    return subprocess.run(cmd, cwd=cwd, text=True)

def wait_for_complete(file_path, timeout=10):
    """Wait until file size stops changing (fully written)"""
    last_size = -1
    stable_count = 0
    start_time = time.time()

    while time.time() - start_time < timeout:
        try:
            size = file_path.stat().st_size
        except FileNotFoundError:
            return False  # file disappeared
        if size == last_size:
            stable_count += 1
            if stable_count >= 3:  # stable for 3 checks
                return True
        else:
            stable_count = 0
            last_size = size
        time.sleep(0.2)
    return False

class Handler(FileSystemEventHandler):
    def on_created(self, event):
        p = Path(event.src_path)
        if p.is_file() and p.suffix.lower() == ".patch":
            if wait_for_complete(p):
                print(f"[Watcher] New patch: {p.name}")
                run([sys.executable, str(APPLY), str(p)], cwd=ROOT)
            else:
                print(f"[Watcher] Timeout waiting for {p.name} to finish writing.")

def main():
    INCOMING.mkdir(parents=True, exist_ok=True)
    print(f"[Watcher] Watching {INCOMING}")
    obs = Observer()
    obs.schedule(Handler(), str(INCOMING), recursive=False)
    obs.start()
    try:
        while True:
            time.sleep(0.5)
    except KeyboardInterrupt:
        pass
    finally:
        obs.stop()
        obs.join()

if __name__ == "__main__":
    main()
