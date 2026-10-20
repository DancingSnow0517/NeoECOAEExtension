"""Prepare the existing isolated verification checkout without touching normal run saves."""
from pathlib import Path
import argparse
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("snapshot", type=Path)
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[2]
    snapshot = args.snapshot.resolve()
    if snapshot == project or not (snapshot / "gradlew.bat").is_file():
        raise SystemExit("Use an existing separate verification checkout.")
    # Bring only the current source snapshot forward; keep all local runtime helpers intact.
    tracked = subprocess.check_output(
        ["git", "-c", "core.quotepath=false", "diff", "--name-only", "HEAD", "--", "src", "core", "build.gradle", "gradle.properties"],
        cwd=project, text=True, encoding="utf-8"
    ).splitlines()
    untracked = subprocess.check_output(
        ["git", "-c", "core.quotepath=false", "ls-files", "--others", "--exclude-standard", "--", "src", "core"],
        cwd=project, text=True, encoding="utf-8"
    ).splitlines()
    for relative in sorted(set(tracked + untracked)):
        source, target = project / relative, snapshot / relative
        if source.is_file():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
    shutil.copytree(project / "tools/machine-promo", snapshot / "tools/machine-promo", dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns("__pycache__"))
    baseline = snapshot / ".gradle/data-card-client"
    client = snapshot / ".gradle/machine-promo-client"
    client.mkdir(parents=True, exist_ok=True)
    for folder in ("config", "mods"):
        if not (client / folder).exists():
            shutil.copytree(baseline / folder, client / folder)
    world = client / "saves/machine-marketing-preview"
    if not world.exists():
        shutil.copytree(baseline / "saves/data-workstation-current-preview", world,
                        ignore=shutil.ignore_patterns("session.lock"))
    options_path = client / "options.txt"
    options = (options_path if options_path.exists() else baseline / "options.txt").read_text(encoding="utf-8")
    settings = {"lang": "en_us", "guiScale": "3", "fullscreen": "false", "pauseOnLostFocus": "false",
                "renderDistance": "8", "mipmapLevels": "0"}
    lines = options.splitlines()
    for key, value in settings.items():
        lines = [line for line in lines if not line.startswith(key + ":")]
        lines.append(key + ":" + value)
    options_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Prepared isolated English client: {client}")


if __name__ == "__main__":
    main()
