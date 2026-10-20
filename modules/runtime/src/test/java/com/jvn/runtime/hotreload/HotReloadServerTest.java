package com.jvn.runtime.hotreload;

import org.junit.jupiter.api.Test;
import java.net.Socket;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class HotReloadServerTest {
  @Test
  void listensOnlyOnLoopbackAndSurvivesCallbackFailures() throws Exception {
    var received = new LinkedBlockingQueue<String>();
    try (var server = HotReloadServer.start(0, path -> {
      if (path.equals("fail")) throw new IllegalStateException("expected");
      received.add(path);
    }); var client = new Socket("127.0.0.1", server.port())) {
      assertTrue(server.address().isLoopbackAddress());
      client.getOutputStream().write("UNKNOWN x\nRELOAD   \nRELOAD fail\nRELOAD story/é.vns\r\n".getBytes(StandardCharsets.UTF_8));
      assertEquals("story/é.vns", received.poll(3, TimeUnit.SECONDS));
      assertTrue(received.isEmpty());
    }
  }

  @Test
  void immediateStopReleasesPortAndConnectedIdleClient() throws Exception {
    var server = HotReloadServer.start(0, path -> {});
    int port = server.port();
    try (server; var client = new Socket("127.0.0.1", port)) {
      client.setSoTimeout(3000);
      server.stop();
      server.stop();
      assertEquals(-1, client.getInputStream().read());
    }
    try (ServerSocket rebound = new ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
      assertEquals(port, rebound.getLocalPort());
    }
  }

  @Test
  void oversizedCommandsAreDisconnectedWithoutInvokingCallback() throws Exception {
    var received = new LinkedBlockingQueue<String>();
    try (var server = HotReloadServer.start(0, received::add);
         var client = new Socket("127.0.0.1", server.port())) {
      client.setSoTimeout(3000);
      client.getOutputStream().write(("RELOAD " + "a".repeat(8192)).getBytes(StandardCharsets.UTF_8));
      assertEquals(-1, client.getInputStream().read());
      assertTrue(received.isEmpty());
    }
  }

  @Test
  void invalidOrOccupiedConfiguredPortsReturnNull() throws Exception {
    String previous = System.getProperty("jvn.hotReload.port");
    try {
      for (String value : new String[] {"-1", "0", "65536", "invalid"}) {
        System.setProperty("jvn.hotReload.port", value);
        assertNull(HotReloadServer.startIfEnabled(path -> {}));
      }
      try (ServerSocket occupied = new ServerSocket(0)) {
        System.setProperty("jvn.hotReload.port", Integer.toString(occupied.getLocalPort()));
        assertNull(HotReloadServer.startIfEnabled(path -> {}));
      }
    } finally {
      if (previous == null) System.clearProperty("jvn.hotReload.port");
      else System.setProperty("jvn.hotReload.port", previous);
    }
  }
}
