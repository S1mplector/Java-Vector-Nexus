"""Build Engine Hub installers and portable packages on their target platform."""
import argparse
import hashlib
import os
import platform
import plistlib
import re
import shutil
import struct
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

LINUX_PACKAGE = "jvn-engine-hub"


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


def smoke_launcher(binary, extract, checkout=None):
    run(binary, "--extract-only", env={**os.environ, "JVN_PACKAGED_ENGINE_ROOT": str(extract)}, timeout=240)
    engines = list(extract.glob("*/*/engine"))
    if len(engines) != 1 or not (engines[0] / "modules/editor/build.gradle.kts").is_file():
        raise RuntimeError("Native launcher did not extract the engine and editor")
    # The Java package named "build" and the course must survive workspace exclusions.
    editor = Path("modules/editor/src/main")
    sources = editor / "java/com/jvn/editor/ui"
    templates = editor / "resources/com/jvn/editor/templates/new-project/guided-tutorial/scripts"
    checkout = checkout or Path(__file__).resolve().parents[1]
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


def linux_debian_package(app, icon, version, stem, arch, dest, work):
    if not shutil.which("dpkg-deb"):
        raise RuntimeError("Install dpkg-deb to build the Linux package")
    staging = work / "deb"
    prefix = Path("opt") / LINUX_PACKAGE
    deb_arch = {"x64": "amd64", "arm64": "arm64"}[arch]
    shutil.copytree(app, staging / prefix, symlinks=True)

    binary = staging / "usr/bin" / LINUX_PACKAGE
    binary.parent.mkdir(parents=True)
    binary.symlink_to(Path("/") / prefix / "bin" / app.name)
    desktop = staging / "usr/share/applications" / (LINUX_PACKAGE + ".desktop")
    desktop.parent.mkdir(parents=True)
    desktop.write_text(
        "[Desktop Entry]\n"
        "Type=Application\n"
        "Name=JVN Engine Hub\n"
        "Comment=Launch and manage the Java Vector Nexus editor\n"
        f"Exec={LINUX_PACKAGE}\n"
        f"Icon={LINUX_PACKAGE}\n"
        "Categories=Development;IDE;\n"
        "Terminal=false\n",
        encoding="utf-8",
    )
    packaged_icon = staging / "usr/share/icons/hicolor/scalable/apps" / (LINUX_PACKAGE + ".svg")
    packaged_icon.parent.mkdir(parents=True)
    shutil.copyfile(icon.with_suffix(".svg"), packaged_icon)

    control = staging / "DEBIAN/control"
    control.parent.mkdir()
    control.write_text(
        f"Package: {LINUX_PACKAGE}\n"
        f"Version: {version}\n"
        f"Architecture: {deb_arch}\n"
        "Section: devel\n"
        "Priority: optional\n"
        "Maintainer: JVN <141715261+S1mplector@users.noreply.github.com>\n"
        "Depends: libc6 (>= 2.35), libgcc-s1, libstdc++6, zlib1g, libfreetype6, libfontconfig1,\n"
        " libx11-6, libxext6, libxi6, libxrender1, libxtst6, libgtk-3-0t64 | libgtk-3-0,\n"
        " libasound2t64 | libasound2, libgl1, fonts-dejavu-core\n"
        "Homepage: https://github.com/S1mplector/Java-Vector-Nexus\n"
        "Description: Java Vector Nexus desktop launcher and editor workspace\n"
        " Includes a Java 21 development kit and the matching JVN engine source.\n",
        encoding="utf-8",
    )
    package = dest / (stem + ".deb")
    run("dpkg-deb", "--root-owner-group", "-Zgzip", "--build", staging, package)
    return package


def smoke_installer(installer, work, checkout=None):
    installed = work / "installed"
    if installer.suffix == ".exe":
        silent = ["/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART"]
        run(installer, *silent, "/SP-", f"/DIR={installed}", timeout=300)
        try:
            smoke_launcher(installed / "JVN Engine Hub.exe", work / "installed-smoke", checkout)
        finally:
            run(next(installed.glob("unins*.exe")), *silent, timeout=180)
    elif installer.suffix == ".deb":
        run("dpkg-deb", "--extract", installer, installed)
        smoke_launcher(installed / "opt" / LINUX_PACKAGE / "bin/JVN Engine Hub", work / "installed-smoke", checkout)
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
        smoke_launcher(installed / "JVN Engine Hub.app/Contents/MacOS/JVN Engine Hub", work / "installed-smoke", checkout)


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
    parser.add_argument("--source-root", type=Path,
                        help="source checkout used to build the input jar")
    parser.add_argument("--deb-only", action="store_true",
                        help="build just the Debian package on Linux")
    parser.add_argument("--test-install", action="store_true",
                        help="smoke test the finished installer")
    args = parser.parse_args()
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", args.version):
        parser.error("version must be a numeric major.minor.patch")
    jar = args.jar.resolve(strict=True)
    root = args.source_root.resolve(strict=True) if args.source_root else Path(__file__).resolve().parents[1]
    icons = root / "modules/hub/src/main/resources/com/jvn/hub/images"
    jdk = Path(os.environ["JAVA_HOME"]).resolve(strict=True)
    windows = sys.platform == "win32"
    mac = sys.platform == "darwin"
    system = "windows" if windows else "macos" if mac else "linux"
    if args.deb_only and not sys.platform.startswith("linux"):
        parser.error("--deb-only requires Linux")
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
        smoke_launcher(binary, work / "smoke", root)
        if windows:
            installer = windows_installer(app, icon, args.version, stem, arch, products)
        elif mac:
            installer = mac_disk_image(app, stem, products, work)
        elif args.deb_only or shutil.which("dpkg-deb"):
            installer = linux_debian_package(app, icon, args.version, stem, arch, products, work)
        else:
            installer = None
        artifacts = []
        if installer is not None:
            if args.test_install:
                smoke_installer(installer, work, root)
            artifacts.append(installer)
        if not args.deb_only:
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
