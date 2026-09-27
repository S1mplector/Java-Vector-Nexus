package com.jvn.licensing;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit acceptance utilities. No command infers consent from launching JVN. */
public final class LicenseCli {
  private LicenseCli() {}
  public static void main(String[] args) {
    int code = run(args, AcceptanceStore.local(), System.out, System.err);
    if (code != 0) System.exit(code);
  }

  public static int run(String[] args, AcceptanceStore store, PrintStream out, PrintStream err) {
    try {
      String command = args.length == 0 ? "status" : args[0];
      if (command.equals("verify") && (args.length == 2 || args.length == 3)) {
        var result = AcceptanceStore.verify(Path.of(args[1]));
        out.println(result.detail());
        out.println("Key SHA-256: " + result.keyFingerprint());
        if (args.length == 3 && !args[2].equals(result.keyFingerprint())) {
          err.println("Previously trusted key fingerprint does not match");
          return 2;
        }
        return result.valid() ? 0 : 2;
      }
      if (command.equals("export") && args.length == 3) {
        AcceptanceStore.exportReceipt(Path.of(args[1]), Path.of(args[2]));
        out.println("Receipt exported (contains your supplied name; no private key): " + args[2]);
        return 0;
      }
      if (command.equals("verify-release") && args.length == 3) {
        var expectedFile = Path.of(args[2]);
        java.util.Properties expected;
        try (var input = Files.newInputStream(expectedFile)) { expected = ReleaseIntegrityVerifier.readProperties(input); }
        Path artifact = Path.of(args[1]);
        if (Files.isDirectory(artifact)) ReleaseIntegrityVerifier.verifyDirectory(artifact, expected);
        else ReleaseIntegrityVerifier.verifyZip(artifact, expected);
        out.println("Final settings-credit and runtime integrity verification passed: " + artifact);
        return 0;
      }
      LicenseTerms terms = LicenseTerms.bundled();
      switch (command) {
        case "require" -> {
          if (args.length != 1) return usage(err);
          store.requireCurrent(terms);
          out.println("Current JVN license acceptance verified.");
        }
        case "show" -> {
          if (args.length != 1) return usage(err);
          out.println(terms.license());
          out.println(terms.terms());
          out.println("Consent statement: " + LicenseTerms.CONSENT);
          out.println("Bundle SHA-256: " + terms.bundleHash());
        }
        case "status" -> {
          if (args.length > 1) return usage(err);
          var status = store.status(terms);
          out.println(status.state() + ": " + status.detail());
          out.println("Local storage: " + store.root());
          out.println("Current bundle SHA-256: " + terms.bundleHash());
          if (status.receipt() != null) out.println("Receipt: " + status.receipt());
          out.println("Enforcement enabled for developer tools and exports. Identity and time are not independently verified.");
        }
        case "save-documents" -> {
          if (args.length != 2) return usage(err);
          Path destination = Path.of(args[1]);
          Files.createDirectory(destination);
          Files.writeString(destination.resolve("LICENSE.md"), terms.license());
          Files.writeString(destination.resolve("TERMS.md"), terms.terms());
          out.println("Documents saved to " + destination);
        }
        case "accept" -> {
          // Positional arguments keep the acknowledgement unambiguous and reject unknown flags.
          if ((args.length != 4 && args.length != 5) || !"--agree".equals(args[1])) return usage(err);
          Path receipt = store.accept(terms, args[2], args[3], args.length == 5 ? args[4] : "",
              LicenseTerms.applicationVersion(), "cli-explicit-agree-v1", true);
          out.println("Acceptance saved: " + receipt);
          out.println("Local signature only; no remote evidence or verified identity.");
        }
        default -> { return usage(err); }
      }
      return 0;
    } catch (Exception e) {
      err.println("License operation failed: " + e.getMessage());
      return 2;
    }
  }

  private static int usage(PrintStream out) {
    out.println("Usage: require | status | show | save-documents <new-directory>\n"
        + "  accept --agree <reviewed-bundle-sha256> <name> [organization]\n"
        + "  verify <receipt-directory> [previously-trusted-key-sha256]\n"
        + "  export <receipt-directory> <new-zip-file>\n"
        + "Review 'show' before accepting. Do not accept on another person's behalf without authority.");
    return 2;
  }
}
