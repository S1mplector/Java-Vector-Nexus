# JVN Terms of Use and Electronic Acceptance

Version 1.1 — 27 September 2026

## 1. Relationship to the software license

These Terms govern the electronic acceptance process for Java Vector
Nexus (JVN). The JVN Source-Available Attribution License presented alongside
these Terms governs use and distribution of the engine and utilities. That
License controls in the event of an inconsistency concerning software rights.
Acceptance does not transfer ownership of independently authored game content,
waive rights that cannot lawfully be waived, or extinguish independently held
permissions. No automatic assignment of copyright results from a breach.

## 2. Express agreement

By entering Your name, selecting the agreement checkbox, and activating
“Accept and save receipt”, You expressly agree to the displayed License and
these Terms and intend that action to record Your electronic acceptance.
If You identify an organization, You represent that You are authorized to
accept on its behalf. A name supplied here is self-declared, not verified.
You may review and save the documents before deciding. Closing the page or
choosing “Not now” does not record acceptance. Controls are never preselected.

JVN 0.7.0 requires a valid local acceptance of the current License and Terms
before opening developer tools or producing and publishing game exports.
Declining or closing the required acceptance page exits the developer tool;
command-line builds fail with instructions for reviewing and accepting the
terms. Reading documents and verifying receipts do not require acceptance.
Game players are not required to accept these developer terms to play an
exported game. This technical enforcement does not waive or replace the
License's mandatory player-visible settings credit.

## 3. Record contents and local storage

On express acceptance, JVN stores a receipt on Your computer containing the
name and optional organization You supplied; a locally generated receipt
identifier; the computer-reported UTC time; the application version and
acceptance interface; the consent statement; copies of the displayed License
and Terms; their version identifiers and SHA-256 fingerprints; and a digital
signature made with a locally generated installation key.

The default location is `.jvn/legal` within Your operating-system user home.
The application displays the resolved location. The private signing key
remains in that local directory and is not included in receipt exports.
No email address, account password, hardware identifier, IP address, or machine
fingerprint is collected by this acceptance feature. No record is automatically
transmitted to S1mplector, the maintainers, or a remote service.

You may inspect, export, back up, or delete these local files. Exported receipts
contain the name and organization You supplied; share them only deliberately.
Deletion removes the application's local evidence and may cause a future
acceptance check to report no record. Deletion does not, by itself, rescind an
agreement validly made. Exported or independently retained copies are not
deleted when You delete the local files. Local records remain until You delete
them; JVN does not silently expire or remotely retain them.

## 4. Document changes

A receipt records only the document versions and contents actually presented
when it was created. Changing either document produces a different fingerprint
and makes an earlier receipt out of date for the current document set. No new
acceptance is inferred from an update. Historical receipts remain available.

## 5. Evidential limits

A locally generated signature supports integrity verification against its
public key. It is not identity verification, a certificate issued by a trusted
third party, a trusted timestamp, or conclusive proof that a particular person
accepted the documents. A person controlling the computer and signing key can
create or replace records. The computer-reported time can be inaccurate.

A maintainer seeking to rely on a receipt must assess its provenance and any
independent corroboration. This feature does not promise legal enforceability
in every jurisdiction. Any future account verification, remote retention, or
trusted timestamp service requires separate implementation and appropriate
notice; none is enabled by these Terms.
