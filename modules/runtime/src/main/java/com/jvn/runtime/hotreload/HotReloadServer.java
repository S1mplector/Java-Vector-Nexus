package com.jvn.runtime.hotreload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Loopback-only TCP listener for {@code RELOAD <path>} messages from the editor.
 * Enable with {@code -Djvn.hotReload.port=NNNN}. Callbacks run on background
 * threads and must dispatch UI changes to the appropriate application thread.
 * Stop closes both the listening socket and every connected client.
 */
public final class HotReloadServer implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(HotReloadServer.class);
  private static final String PROP_PORT = "jvn.hotReload.port";
  private static final int MAX_COMMAND_LENGTH = 8192;
  private final Consumer<String> reloadCallback;
  private final ServerSocket serverSocket;
  private final Set<Socket> clients = new HashSet<>();
  private boolean stopped;
  private final ThreadPoolExecutor workers = new ThreadPoolExecutor(
      2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16), r -> {
        Thread thread = new Thread(r, "jvn-hotreload-client");
        thread.setDaemon(true);
        return thread;
      });

  private HotReloadServer(int port, Consumer<String> callback) throws IOException {
    reloadCallback = Objects.requireNonNull(callback, "reloadCallback");
    serverSocket = new ServerSocket();
    try {
      serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
    } catch (IOException | RuntimeException failure) {
      try { serverSocket.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
      throw failure;
    }
  }

  /** Returns null when disabled, misconfigured, or the port cannot be bound. */
  public static HotReloadServer startIfEnabled(Consumer<String> reloadCallback) {
    String value = System.getProperty(PROP_PORT);
    if (value == null || value.isBlank()) return null;
    try {
      int port = Integer.parseInt(value.trim());
      if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535");
      return start(port, reloadCallback);
    } catch (IOException | IllegalArgumentException ex) {
      log.warn("HotReloadServer: cannot start on port '{}': {}", value, ex.getMessage());
      return null;
    }
  }

  // Package-visible to permit isolated tests with an OS-assigned ephemeral port.
  static HotReloadServer start(int port, Consumer<String> callback) throws IOException {
    HotReloadServer server = new HotReloadServer(port, callback);
    Thread listener = new Thread(server::acceptClients, "jvn-hotreload-server");
    listener.setDaemon(true);
    listener.start();
    log.info("HotReloadServer: listening on loopback port {}", server.port());
    return server;
  }

  int port() { return serverSocket.getLocalPort(); }
  InetAddress address() { return serverSocket.getInetAddress(); }

  private void acceptClients() {
    try {
      while (!serverSocket.isClosed()) {
        Socket client = serverSocket.accept();
        synchronized (clients) {
          if (stopped) {
            closeSocket(client);
            return;
          }
          clients.add(client);
          try {
            client.setSoTimeout(2000);
            workers.execute(() -> handleClient(client));
          } catch (IOException | RejectedExecutionException ex) {
            clients.remove(client);
            closeSocket(client);
          }
        }
      }
    } catch (IOException ex) {
      if (!serverSocket.isClosed()) log.warn("HotReloadServer: accept failed: {}", ex.getMessage());
    } finally {
      stop();
    }
  }

  private void handleClient(Socket client) {
    try (client; BufferedReader reader = new BufferedReader(
        new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = readCommand(reader)) != null) {
        line = line.trim();
        if (!line.startsWith("RELOAD ")) continue;
        String path = line.substring("RELOAD ".length()).trim();
        if (path.isEmpty()) continue;
        try {
          reloadCallback.accept(path);
        } catch (Exception ex) {
          log.warn("HotReloadServer: reload callback failed for '{}': {}", path, ex.getMessage());
        }
      }
    } catch (IOException ex) {
      log.trace("HotReloadServer: client connection closed: {}", ex.getMessage());
    } finally {
      synchronized (clients) { clients.remove(client); }
    }
  }

  private static String readCommand(BufferedReader reader) throws IOException {
    StringBuilder line = new StringBuilder();
    int value;
    while ((value = reader.read()) != -1) {
      if (value == '\n') return line.toString();
      if (line.length() >= MAX_COMMAND_LENGTH) throw new IOException("Hot reload command is too long");
      line.append((char) value);
    }
    return line.isEmpty() ? null : line.toString();
  }

  private static void closeSocket(Socket socket) {
    try { socket.close(); } catch (IOException ignored) { /* Best-effort shutdown. */ }
  }

  public void stop() {
    synchronized (clients) {
      if (stopped) return;
      stopped = true;
      try { serverSocket.close(); } catch (IOException ex) {
        log.debug("HotReloadServer: close failed: {}", ex.getMessage());
      }
      clients.forEach(HotReloadServer::closeSocket);
      clients.clear();
      workers.shutdownNow();
    }
  }

  @Override
  public void close() { stop(); }
}
