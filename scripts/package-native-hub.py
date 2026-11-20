"""Build, launch-test and archive the native Engine Hub app on the current host."""
import argparse
import hashlib
import os
import platform
import shutil
import struct
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path


def run(*args, **options):
    subprocess.run([str(arg) for arg in args], check=True, **options)


def mac_icon(source, work):
    chunks = bytearray()
    for kind, size in ((b"icp4", 16), (b"icp5", 32), (b"ic07", 128), (b"ic08", 256),
                       (b"ic09", 512), (b"ic10", 1024)):
        target = work / f"icon-{size}.png"
        run("sips", "-s", "format", "png", "-z", size, size, source, "--out", target,
            stdout=subprocess.DEVNULL)
        data = target.read_bytes()
        chunks.extend(struct.pack(">4sI", kind, len(data) + 8))
        chunks.extend(data)
    result = work / "Hub.icns"
    result.write_bytes(struct.pack(">4sI", b"icns", len(chunks) + 8) + chunks)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version")
    parser.add_argument("jar", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    jar = args.jar.resolve(strict=True)
    root = Path(__file__).resolve().parents[1]
    icons = root / "modules/hub/src/main/resources/com/jvn/hub/images"
    jdk = Path(os.environ["JAVA_HOME"]).resolve(strict=True)
    windows = sys.platform == "win32"
    mac = sys.platform == "darwin"
    system = "windows" if windows else "macos" if mac else "linux"
    arch = {"aarch64": "arm64", "arm64": "arm64", "x86_64": "x64", "amd64": "x64"}[platform.machine().lower()]
    stem = f"jvn-engine-hub-{args.version}-{system}-{arch}"
    args.output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="jvn-native-") as temp:
        work = Path(temp)
        inputs = work / "input"
        inputs.mkdir()
        shutil.copyfile(jar, inputs / "engine-hub.jar")
        image = work / "image"
        icon = mac_icon(icons / "jvn_hub_icon.png", work) if mac else icons / ("jvn_hub_icon.ico" if windows else "jvn_hub_icon.png")
        # macOS requires a positive first component for CFBundleVersion. The Hub and archive
        # keep the project version, including pre-1.0 releases.
        native_version = args.version
        if mac and native_version.startswith("0."):
            native_version = "1." + native_version[2:]
        run(jdk / "bin" / ("jpackage.exe" if windows else "jpackage"),
            "--type", "app-image", "--name", "JVN Engine Hub", "--dest", image,
            "--vendor", "JVN", "--app-version", native_version, "--icon", icon,
            "--input", inputs, "--main-jar", "engine-hub.jar",
            "--main-class", "com.jvn.hub.PackagedHubLauncher", "--runtime-image", jdk)
        app = image / ("JVN Engine Hub.app" if mac else "JVN Engine Hub")
        binary = app / ("Contents/MacOS/JVN Engine Hub" if mac else "JVN Engine Hub.exe" if windows else "bin/JVN Engine Hub")
        extract = work / "smoke"
        run(binary, "--extract-only", env={**os.environ, "JVN_PACKAGED_ENGINE_ROOT": str(extract)}, timeout=240)
        engines = list(extract.glob("*/*/engine"))
        if len(engines) != 1 or not (engines[0] / "modules/editor/build.gradle.kts").is_file():
            raise RuntimeError("Native launcher did not extract the engine and editor")
        if not windows and not os.access(engines[0] / "scripts/launch-app.sh", os.X_OK):
            raise RuntimeError("The extracted editor launcher is not executable")
        if windows:
            archive = Path(shutil.make_archive(str(args.output / stem), "zip", image, app.name))
        else:
            archive = args.output / (stem + ".tar.gz")
            with tarfile.open(archive, "w:gz", compresslevel=5) as bundle:
                bundle.add(app, arcname=app.name)
        with archive.open("rb") as source:
            checksum = hashlib.file_digest(source, "sha256").hexdigest()
        (args.output / (archive.name + ".sha256")).write_text(f"{checksum}  {archive.name}\n", encoding="ascii")
        print(archive)


if __name__ == "__main__":
    main()
