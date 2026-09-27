# License Acceptance and Receipts

JVN provides a acceptance page in **Help → License & Terms** in the
Engine Hub, Editor, and Launcher. It uses the existing Aero icons and graphite
editor surfaces. Current acceptance is required before Hub, Editor, or Launcher startup and before
game packaging or publishing. Read-only license utilities and ordinary source
compilation/tests remain available. Exported-game players are not gated.
The [License](../../../LICENSE.md) and [Terms of Use](../../../TERMS.md) govern
the documents presented; this page describes the implementation.

## Player-visible credit and developer acceptance are distinct

A developer receipt records agreement to a document set. It does not certify
that a game displays the required JVN credit. The acceptance system does not
insert a watermark or put a developer's receipt, name, or private key into a
game export. Exported games retain their separate license notices.

## Review and accept

1. Open **Help → License & Terms** and review both document tabs.
2. Optionally use **Save documents** to keep copies before deciding.
3. Enter your name and, if applicable, an organization you are authorized to
   represent. These statements are not identity-verified.
4. Select the initially unchecked agreement box, then **Accept and save receipt**.
5. Use **Export receipt** to save a ZIP containing the record and its documents.

Opening, reading, entering a name, checking the box alone, or closing the page
never saves acceptance. A failed write is reported; success is shown only after
the completed receipt is verified and published to the local store. The checkbox
resets after a successful save. **Decline and exit** on the required startup page exits the tool without recording consent.
An exported receipt contains the name and organization supplied by the user.

## Storage and receipt format

Default location: `~/.jvn/legal` (the operating-system user's home directory on
all supported desktop systems). The page displays the resolved absolute path.
The Java system property `jvn.legalDir` can override it for tests and diagnostics.
No files are created by a read-only status check.

```text
legal/
  acceptance.lock
  identity/
    private-key.pk8          local Ed25519 private key; never exported
    public-key.der           local Ed25519 public key (X.509 encoding)
  receipts/
    <random-UUID>/
      receipt.properties
      LICENSE.md
      TERMS.md
      public-key.der
```

Each receipt records schema `jvn-acceptance-1`, a random receipt ID,
computer-reported UTC time, self-declared name and optional organization,
application version, acceptance interface, exact consent statement, document
versions, document SHA-256 hashes, document-set hash, key fingerprint, and an
Ed25519 signature. There is no hardware fingerprint or automatic upload.

Document identity uses UTF-8 text with line endings normalized to LF so Windows
and Unix checkouts agree. The displayed text and saved snapshots use this same
normalization. The bundle fingerprint is SHA-256 of UTF-8:

```text
JVN-LEGAL-1\n<license-sha256>\n<terms-sha256>
```

There is no trailing newline in that fingerprint input. Signature input is the
lexicographically sorted set of all receipt properties except `signature`.
Each key and then its value is encoded as UTF-8 preceded by a four-byte,
big-endian byte length. The signature is Base64 encoded. Java properties
comments and ordering are not signed; parsed key/value content is. Unknown,
missing, or duplicate fields and unknown schemas are rejected. Schema v1 and its
consent statement are fixed: future formats must introduce a new schema with
versioned verification while retaining a verifier for historical v1 receipts.

## Reliability and verification behavior

- Cross-process file locking prevents concurrent key generation and conflicting
  saves. A competing writer receives a retryable error rather than apparent success.
- New identities and receipts are staged in their destination filesystem;
  receipt contents are verified and files flushed before publication. Directory
  moves use atomic moves where supported, with a same-filesystem move fallback.
- Interrupted staging folders are ignored by status checks. Existing completed
  receipts are not overwritten or silently repaired. Damaged signing keys cause
  an error; the application does not silently replace the installation identity.
- Export snapshots are verified before creating the ZIP, written to a temporary
  file, flushed, and moved into place without requesting replacement of an
  existing destination. Private keys are excluded by an explicit allowlist.
- On POSIX filesystems, local directories are restricted to the current user
  and private keys/temporary export files have owner-only permissions. On other
  filesystems, operating-system account-directory permissions apply.
- Verification checks schema, document versions and hashes, consent statement,
  public-key fingerprint, and signature. Receipt file reads have a 1 MiB limit.
- Current local status also checks that the receipt matches the installation's
  public key. Standalone verification checks the included key; an independently
  retained fingerprint may be supplied to the CLI for comparison.

Status is `NOT_ACCEPTED`, `CURRENT`, `OUTDATED`, or `INVALID`. Any content change
in either document requires a new acceptance, even without a version-number
change. Old receipts remain independently verifiable. An invalid record never
satisfies acceptance; a separate valid receipt for the current documents can.

Back up the local store if retaining evidence matters. Users can remove it
manually. Deleting local files removes local evidence; the software cannot
recover them from a server because no remote service receives them.

## Command-line utilities

Run from the repository root:

```bash
./gradlew :licensing:run --args='show'
./gradlew :licensing:run --args='status'
./gradlew :licensing:run --args='save-documents /path/to/new-documents-directory'
```

After personally reviewing the documents and the consent statement printed by
`show`, explicitly accept using the displayed bundle hash and your name:

```bash
./gradlew :licensing:run --args='accept --agree <bundle-sha256> "Your Name" "Optional Organization"'
./gradlew :licensing:run --args='verify /path/to/receipt-directory'
./gradlew :licensing:run --args='verify /path/to/receipt-directory <previously-trusted-key-sha256>'
./gradlew :licensing:run --args='export /path/to/receipt-directory /path/to/new-receipt.zip'
```

Verification takes a receipt directory, including a directory extracted from an
exported ZIP. A signature/hash failure, invalid argument, or storage error returns
exit code 2. `status` is informational and returns 0 even without acceptance;
it is not an enforcement gate. The `require` command returns exit code 2 unless
current acceptance is valid and is used by the export pipeline. These tools are also available in the licensing
module's application distribution, produced by `:licensing:installDist`.

For isolated test records, add `-Djvn.legalDir=/path/to/test-store` to the Gradle
invocation. Do not record acceptance on behalf of someone without authority.

## Evidential boundary and enforcement

The local signature protects integrity relative to a local key. It cannot prove
that a named person clicked, establish a trusted acceptance time, or prevent a
computer's owner from fabricating an entire record. A self-issued public key is
not a certificate binding it to an identified person. No automatic remote
collection, account authentication, independent timestamping, or remote audit
log is implemented. Do not describe these receipts as conclusive legal proof.

These distinctions matter because signature applications need assurance of the
key owner's identity as well as signature validity, and a signed local clock
value is not a trusted timestamp. See [NIST's identity-assurance guidance](https://www.nist.gov/publications/recommendation-obtaining-assurances-digital-signature-applications)
and [digital-signature timeliness guidance](https://www.nist.gov/publications/recommendation-digital-signature-timeliness).

The current enforcement integration consumes `AcceptanceStore.status()` and the
immutable `LicenseTerms` document set. Missing, invalid, and obsolete records all fail the developer-tool and export gates. Stronger evidence would require a
separately designed authenticated remote receipt service and trusted timestamps,
with appropriate privacy notice and retention controls. No dormant network
endpoint or secret master key has been added.

## Validation

Backend tests cover explicit consent, outdated documents, tampering, malformed
records, duplicate fields, missing files, corrupt/mismatched keys, concurrent
writers, interrupted staging, private permissions, export failures, and
verification after the original store is removed. JavaFX and Swing tests cover
the actual acceptance controls and saving through the UI using temporary stores.

```bash
./gradlew :licensing:test :hub:test :editor:test
node scripts/doc-lint.mjs --strict
```

UI tests require a working display and report skips when the toolkit is not
available. No test uses a real user's acceptance store.
