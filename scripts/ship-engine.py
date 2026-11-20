#!/usr/bin/env python3
"""Start a native Engine Hub release by pushing the current commit as an engine tag."""
import argparse
import re
import subprocess
from pathlib import Path


def release_version(value):
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", value):
        raise argparse.ArgumentTypeError("use a version such as 0.8.1")
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", type=release_version)
    parser.add_argument("--dry-run", action="store_true", help="show the commit, tag and destination")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    tag = "engine-v" + args.version
    print(f"{commit[:12]} → origin/refs/tags/{tag}", flush=True)
    if not args.dry_run:
        subprocess.run(["git", "push", "origin", f"{commit}:refs/tags/{tag}"], cwd=root, check=True)


if __name__ == "__main__":
    main()
