# Game Licensing and JVN Attribution

The [JVN Source-Available Attribution License](../../../LICENSE.md) is the
controlling text. This page explains its application to game releases.

## Public source and proprietary licensing

JVN's engine and utilities are publicly available under a custom proprietary,
source-available license. This is not a claim of OSI approval. Commercial use,
modification, and redistribution are permitted subject to the license.
Distributed JVN versions must have corresponding source publicly available at
no cost, including engine modifications and necessary build instructions.
For an unmodified engine, provide a working link to its exact source revision.
Independent game code, scripts, stories, artwork, music, and plugins may retain
their own licenses and do not have to be publicly released merely for using JVN.

## Required credit in a playable game

For every distributed desktop or mobile build covered by the new license,
include a readable acknowledgement such as:

> Made with JVN (Java Vector Nexus)

JVN 0.7.0 requires the exact form **Made with [official JVN SVG logo] (engine version)**
in a plainly visible Settings menu. The engine supplies a persistent Settings
control and a non-configurable credit. The credit cannot be removed, hidden,
faded, replaced, or pushed behind scrolling. Splash screens and other credits
are supplementary. This applies to every distributed platform variant and update.
Android and iOS remain scaffolds; these terms do not claim implemented mobile exports.

## Exported notices and release checks

Portable desktop archives, bundled-runtime archives, and native package app
content include `JVN-LICENSE.md`, `JVN-ATTRIBUTION.txt`, and `JVN-EXPORT-CONTRACT.properties`. Native installers retain the game's separately
configured license file; JVN's notice does not replace the game's EULA.
Archive verification checks that the JVN notice files are present.

Before distribution:

- Open the actual build and verify that its in-game JVN credit is readable.
- Retain the packaged JVN notices and all applicable third-party notices.
- Provide the exact engine source revision, or publish corresponding modified
  engine source and build instructions as required by the license.
- Check third-party dependency, JDK, JavaFX, font, icon, and media obligations.

The runtime supplies the Settings credit. Final package verification checks
current acceptance, exact runtime jar hashes, SVG identity, engine version, and
license notices before release. Developers remain responsible for actual visual
compliance after any modification; automated checks do not certify legal compliance.

## Noncompliance and ownership

Under the new license, distribution permission for a noncompliant build
terminates. The distributor must stop affected distribution and remove or
disable downloads and listings under its control until compliance is restored
or written permission is obtained. S1mplector and other relevant rights holders,
or their expressly authorized representatives, may demand withdrawal and seek
remedies available under applicable law. Compliance prospectively reinstates
permission without extinguishing claims for earlier breaches.

Missing attribution does not automatically transfer ownership of a game or its
independent content. Maintainers enforce only rights they hold or are authorized
to represent; platform removal and judicial remedies are not guaranteed.

## Transition from MIT

Earlier MIT grants remain valid. This change cannot retroactively require
in-game credit for games made solely under those grants, revoke earlier MIT
permissions, or transfer another contributor's copyright. Earlier versions
retain their original terms and notice obligations. Separately licensed
third-party components retain their licenses.

The new terms apply to releases expressly made under them and only to rights
the relevant licensors can grant. Before publishing a release represented as
exclusively covered by the new terms, maintainers must establish the provenance
and necessary authorization for contributed material. Obtain qualified legal
review of this custom license and the intended enforcement process before
relying on them in a dispute.

## Electronic acceptance

The **Help → License & Terms** page records express acceptance of
the displayed documents. See [License Acceptance and Receipts](license-acceptance.md)
for storage, export, verification, and evidential limits. Startup and export
gates are enabled in 0.7.0.

## Related documentation

- [Build And Release Docs](README.md)
- [Build System](build-system.md)
- [Deployment & Packaging](deployment.md)
