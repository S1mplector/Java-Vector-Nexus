#!/usr/bin/env bash
set -euo pipefail

package="$(realpath "$1")"
version="$2"
for ubuntu in 22.04 24.04; do
  docker run --rm --mount "type=bind,source=$package,target=/hub.deb,readonly" \
    --env "JVN_TEST_VERSION=$version" "ubuntu:$ubuntu" bash -euc '
      export DEBIAN_FRONTEND=noninteractive
      apt-get update -qq
      apt-get install -y -qq --no-install-recommends /hub.deb
      test "$(dpkg-query -W -f="\${Version}" jvn-engine-hub)" = "$JVN_TEST_VERSION"
      grep -Fx "Exec=jvn-engine-hub" /usr/share/applications/jvn-engine-hub.desktop
      test -f /usr/share/icons/hicolor/scalable/apps/jvn-engine-hub.svg
      install -d -o nobody -g nogroup /tmp/jvn-test
      su -s /bin/sh nobody -c "HOME=/tmp/jvn-test JVN_PACKAGED_ENGINE_ROOT=/tmp/jvn-test/workspace jvn-engine-hub --extract-only"
      engine=$(find /tmp/jvn-test/workspace -type d -name engine -print -quit)
      test -f "$engine/modules/editor/build.gradle.kts"
      test -x "$engine/scripts/launch-app.sh"
      grep -Fx "jvnVersion=$JVN_TEST_VERSION" "$engine/gradle.properties"
      apt-get purge -y -qq jvn-engine-hub
      test ! -L /usr/bin/jvn-engine-hub
      test ! -e /usr/share/applications/jvn-engine-hub.desktop
      test -f "$engine/modules/editor/build.gradle.kts"
    '
  echo "Verified install, launch, and removal on Ubuntu $ubuntu"
done
