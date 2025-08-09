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

class Handler(FileSystemEventHandler):
    def on_created(self, event):
        p = Path(event.src_path)
        if p.is_file() and p.suffix.lower() == ".patch":
            # small wait so editors finish writing
            time.sleep(0.5)
            print(f"[Watcher] New patch: {p.name}")
            run([sys.executable, str(APPLY), str(p)], cwd=ROOT)

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
