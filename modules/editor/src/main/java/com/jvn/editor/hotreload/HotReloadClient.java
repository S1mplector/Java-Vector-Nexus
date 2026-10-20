package com.jvn.editor.hotreload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Sends a {@code RELOAD <path>} command to a running JVN runtime's HotReloadServer.
 *
 * <p>Reads the target port from the system property {@code jvn.hotReload.port}.
 * If the property is absent or the connection fails, the send is silently skipped.</p>
 */
public final class HotReloadClient {

  private static final Logger log = LoggerFactory.getLogger(HotReloadClient.class);
  private static final String PROP_PORT = "jvn.hotReload.port";

  private HotReloadClient() {}

  /**
   * Fire-and-forget: connects to localhost on the configured port, sends
   * {@code RELOAD <scriptPath>}, then closes the connection.
   *
   * @param scriptPath the asset-relative path of the saved script (e.g. {@code story/ch01.vns})
   */
  public static void sendReload(String scriptPath) {
    if (scriptPath == null || scriptPath.isBlank() || scriptPath.contains("\n") || scriptPath.contains("\r")) return;
    String portProp = System.getProperty(PROP_PORT);
    if (portProp == null || portProp.isBlank()) return;
    int port;
    try {
      port = Integer.parseInt(portProp.trim());
      if (port < 1 || port > 65535) return;
    } catch (NumberFormatException e) {
      log.debug("HotReloadClient: invalid port '{}', skipping reload", portProp);
      return;
    }
    try (Socket sock = new Socket()) {
      sock.connect(new InetSocketAddress("127.0.0.1", port), 1000);
      try (PrintWriter pw = new PrintWriter(
             new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8), true)) {
        pw.println("RELOAD " + scriptPath);
        if (pw.checkError()) throw new IOException("Could not write reload command");
        log.debug("HotReloadClient: sent RELOAD '{}' to port {}", scriptPath, port);
      }
    } catch (IOException e) {
      log.debug("HotReloadClient: could not connect to runtime on port {}: {}", port, e.getMessage());
    }
  }
}
