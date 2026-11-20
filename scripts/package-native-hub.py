"""Build Engine Hub installers and portable packages on their target platform."""
import argparse
import hashlib
import os
import platform
import plistlib
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


def smoke_launcher(binary, extract):
    run(binary, "--extract-only", env={**os.environ, "JVN_PACKAGED_ENGINE_ROOT": str(extract)}, timeout=240)
    engines = list(extract.glob("*/*/engine"))
    if len(engines) != 1 or not (engines[0] / "modules/editor/build.gradle.kts").is_file():
        raise RuntimeError("Native launcher did not extract the engine and editor")
    # The Java package named "build" and the course must survive workspace exclusions.
    editor = Path("modules/editor/src/main")
    sources = editor / "java/com/jvn/editor/ui"
    templates = editor / "resources/com/jvn/editor/templates/new-project/guided-tutorial/scripts"
    checkout = Path(__file__).resolve().parents[1]
    required = [sources / "RunConsoleView.java", sources / "GameBuildPublisherView.java",
                sources / "build/BuildArtifactService.java", templates / "story/tutorial_hub.vns"]
    required.extend(path.relative_to(checkout) for path in (checkout / templates / "tutorial").glob("*.vns"))
    for relative in required:
        if not (engines[0] / relative).is_file() or (engines[0] / relative).read_bytes() != (checkout / relative).read_bytes():
            raise RuntimeError(f"Installed workspace has missing or outdated content: {relative}")
    if sys.platform != "win32" and not os.access(engines[0] / "scripts/launch-app.sh", os.X_OK):
        raise RuntimeError("The extracted editor launcher is not executable")


def windows_installer(app, icon, version, stem, arch, dest):
    compiler = shutil.which("ISCC") or Path(os.environ["ProgramFiles(x86)"]) / "Inno Setup 6/ISCC.exe"
    if not Path(compiler).is_file():
        raise RuntimeError("Install Inno Setup 6 or add ISCC.exe to PATH to build the Windows installer")
    target_arch = "x64compatible" if arch == "x64" else "arm64"
    run(compiler, "/Qp", f"/DHubVersion={version}", f"/DTargetArch={target_arch}",
        f"/DImageDir={app}", f"/DHubIcon={icon}", f"/DOutputDirPath={dest}",
        f"/DInstallerName={stem}", Path(__file__).with_name("windows-hub.iss"))
    return dest / (stem + ".exe")


def mac_disk_image(app, stem, dest, work):
    # Construct an HFS+ filesystem without mounting a writable disk image.
    folder = work / "dmg-contents"
    folder.mkdir()
    run("ditto", app, folder / app.name)
    (folder / "Applications").symlink_to("/Applications")
    filesystem = work / "hub-volume.dmg"
    run("hdiutil", "makehybrid", "-hfs", "-hfs-volume-name", "JVN Engine Hub",
        "-o", filesystem, folder)
    dmg = dest / (stem + ".dmg")
    run("hdiutil", "convert", filesystem, "-format", "UDZO", "-o", dmg)
    run("hdiutil", "verify", dmg)
    return dmg


def smoke_installer(installer, work):
    installed = work / "installed"
    if installer.suffix == ".exe":
        silent = ["/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART"]
        run(installer, *silent, "/SP-", f"/DIR={installed}", timeout=300)
        try:
            smoke_launcher(installed / "JVN Engine Hub.exe", work / "installed-smoke")
        finally:
            run(next(installed.glob("unins*.exe")), *silent, timeout=180)
    else:
        installed.mkdir()
        mount = work / "mounted"
        mount.mkdir()
        run("hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint", mount, installer)
        try:
            if (mount / "Applications").readlink() != Path("/Applications"):
                raise RuntimeError("Disk image is missing the Applications link")
            run("ditto", mount / "JVN Engine Hub.app", installed / "JVN Engine Hub.app")
        finally:
            run("hdiutil", "detach", mount)
        smoke_launcher(installed / "JVN Engine Hub.app/Contents/MacOS/JVN Engine Hub", work / "installed-smoke")


def write_checksum(artifact):
    with artifact.open("rb") as source:
        checksum = hashlib.file_digest(source, "sha256").hexdigest()
    artifact.with_name(artifact.name + ".sha256").write_text(f"{checksum}  {artifact.name}\n", encoding="ascii")
    print(artifact)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version")
    parser.add_argument("jar", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--test-install", action="store_true",
                        help="install and launch the finished EXE or DMG on a clean test machine")
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
    destination = args.output.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="jvn-native-") as temp:
        work = Path(temp)
        inputs = work / "input"
        inputs.mkdir()
        shutil.copyfile(jar, inputs / "engine-hub.jar")
        image = work / "image"
        icon = mac_icon(icons / "jvn_hub_icon.png", work) if mac else icons / ("jvn_hub_icon.ico" if windows else "jvn_hub_icon.png")
        # Apple's build number starts at one. Keep the public version independently in Finder.
        major, minor, patch = args.version.split(".")
        native_version = f"{int(major) + 1}.{minor}.{patch}" if mac else args.version
        run(jdk / "bin" / ("jpackage.exe" if windows else "jpackage"),
            "--type", "app-image", "--name", "JVN Engine Hub", "--dest", image,
            "--vendor", "JVN", "--app-version", native_version, "--icon", icon,
            "--input", inputs, "--main-jar", "engine-hub.jar",
            "--main-class", "com.jvn.hub.PackagedHubLauncher", "--runtime-image", jdk)
        app = image / ("JVN Engine Hub.app" if mac else "JVN Engine Hub")
        binary = app / ("Contents/MacOS/JVN Engine Hub" if mac else "JVN Engine Hub.exe" if windows else "bin/JVN Engine Hub")
        products = work / "products"
        products.mkdir()
        if mac:
            info = app / "Contents/Info.plist"
            properties = plistlib.loads(info.read_bytes())
            properties["CFBundleShortVersionString"] = args.version
            info.write_bytes(plistlib.dumps(properties))
            run("codesign", "--force", "--sign", "-", app)
            run("codesign", "--verify", "--deep", "--strict", app)
        smoke_launcher(binary, work / "smoke")
        artifacts = []
        if windows or mac:
            installer = (windows_installer(app, icon, args.version, stem, arch, products) if windows
                         else mac_disk_image(app, stem, products, work))
            if args.test_install:
                smoke_installer(installer, work)
            artifacts.append(installer)
        if windows:
            archive = Path(shutil.make_archive(str(products / stem), "zip", image, app.name))
        else:
            archive = products / (stem + ".tar.gz")
            with tarfile.open(archive, "w:gz", compresslevel=5) as bundle:
                bundle.add(app, arcname=app.name)
        artifacts.append(archive)
        for artifact in artifacts:
            final = destination / artifact.name
            shutil.move(str(artifact), str(final))
            write_checksum(final)


if __name__ == "__main__":
    main()
