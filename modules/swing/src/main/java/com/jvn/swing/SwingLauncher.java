package com.jvn.swing;

import com.jvn.core.engine.Engine;
import com.jvn.core.scene2d.Scene2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.graphics.Camera2D;
import com.jvn.core.graphics.ViewportScaler2D;
import com.jvn.core.input.InputCode;
import com.jvn.core.scene.Scene;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Locale;

public class SwingLauncher {
  public static void launch(Engine engine) {
    JFrame frame = new JFrame(engine != null && engine.getConfig() != null ? engine.getConfig().title() : "JVN");
    int w = engine != null && engine.getConfig() != null ? engine.getConfig().width() : 960;
    int h = engine != null && engine.getConfig() != null ? engine.getConfig().height() : 540;
    SwingSceneRendererRegistry rendererRegistry = createRendererRegistry(engine);

    JPanel panel = new JPanel() {
      @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (engine != null) {
          Scene current = engine.scenes().peek();
          SwingBlitter2D bl = new SwingBlitter2D(g2);
          boolean rendered = rendererRegistry.render(current,
              new SwingSceneRendererRegistry.RenderContext(g2, bl, getWidth(), getHeight()));
          if (!rendered) {
            g2.setColor(Color.WHITE);
            g2.drawString("JVN Swing - No compatible scene loaded", 20, 30);
          }
          bl.dispose();
        }
        g2.dispose();
      }
    };
    panel.setFocusable(true);
    panel.requestFocusInWindow();

    // Input wiring
    panel.addKeyListener(new KeyAdapter() {
      @Override public void keyPressed(KeyEvent e) {
        if (engine != null && engine.input() != null) engine.input().keyDown(InputCode.key(KeyEvent.getKeyText(e.getKeyCode())));
      }
      @Override public void keyReleased(KeyEvent e) {
        if (engine != null && engine.input() != null) engine.input().keyUp(InputCode.key(KeyEvent.getKeyText(e.getKeyCode())));
      }
    });
    panel.addMouseMotionListener(new MouseMotionAdapter() {
      @Override public void mouseMoved(MouseEvent e) {
        if (engine != null && engine.input() != null) engine.input().setMousePosition(e.getX(), e.getY());
      }
      @Override public void mouseDragged(MouseEvent e) {
        if (engine != null && engine.input() != null) engine.input().setMousePosition(e.getX(), e.getY());
      }
    });
    panel.addMouseListener(new MouseAdapter() {
      @Override public void mousePressed(MouseEvent e) {
        if (engine != null && engine.input() != null) engine.input().mouseDown(mapButton(e.getButton()));
      }
      @Override public void mouseReleased(MouseEvent e) {
        if (engine != null && engine.input() != null) engine.input().mouseUp(mapButton(e.getButton()));
      }
    });
    panel.addMouseWheelListener(e -> {
      if (engine != null && engine.input() != null) engine.input().addScrollDeltaY(-e.getPreciseWheelRotation());
    });

    JPanel root = new JPanel(new BorderLayout());
    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
    toolbar.setBackground(Color.decode("#202020"));
    JButton settings = new JButton("Settings");
    settings.setName("jvn-runtime-settings");
    settings.addActionListener(event -> showEngineSettings(frame));
    toolbar.add(settings); root.add(toolbar, BorderLayout.NORTH); root.add(panel, BorderLayout.CENTER);
    frame.setContentPane(root);
    frame.setSize(w, h);
    frame.setLocationRelativeTo(null);
    applyLinuxDefaultWindowState(frame);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent event) {
        if (engine != null) engine.stop();
      }
    });
    frame.setVisible(true);

    final long[] lastNs = { -1L };
    Timer timer = new Timer(16, evt -> {
      long now = System.nanoTime();
      if (lastNs[0] < 0) { lastNs[0] = now; return; }
      long deltaMs = (now - lastNs[0]) / 1_000_000L;
      lastNs[0] = now;
      if (engine != null) engine.update(deltaMs);
      panel.repaint();
    });
    timer.start();
  }

  static JDialog createEngineSettings(JFrame owner) {
    JDialog dialog = new JDialog(owner, "Settings", true);
    JPanel content = new JPanel(new BorderLayout(12, 12));
    content.setBackground(Color.decode("#202020"));
    content.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
    JLabel title = new JLabel("Engine information"); title.setForeground(Color.WHITE);
    JPanel credit = new JPanel(new FlowLayout(FlowLayout.CENTER, 9, 8));
    credit.setName("jvn-settings-credit"); credit.setOpaque(false);
    JLabel prefix = new JLabel("Made with"); prefix.setForeground(Color.WHITE);
    JLabel logo = new JLabel(new ImageIcon(com.jvn.branding.EngineBranding.logo().getScaledInstance(100, 50, Image.SCALE_SMOOTH)));
    logo.getAccessibleContext().setAccessibleName("JVN");
    JLabel version = new JLabel("(" + com.jvn.branding.EngineBranding.version() + ")"); version.setForeground(Color.WHITE);
    credit.add(prefix); credit.add(logo); credit.add(version);
    credit.getAccessibleContext().setAccessibleName(com.jvn.branding.EngineBranding.accessibleCredit());
    JButton close = new JButton("Close"); close.addActionListener(event -> dialog.dispose());
    content.add(title, BorderLayout.NORTH); content.add(credit, BorderLayout.CENTER); content.add(close, BorderLayout.SOUTH);
    dialog.setContentPane(content); dialog.pack(); dialog.setMinimumSize(new Dimension(360, 210));
    dialog.setSize(400, 240); dialog.setLocationRelativeTo(owner);
    return dialog;
  }

  private static void showEngineSettings(JFrame owner) { createEngineSettings(owner).setVisible(true); }

  private static int mapButton(int awtButton) {
    return switch (awtButton) {
      case MouseEvent.BUTTON1 -> 1;
      case MouseEvent.BUTTON2 -> 2;
      case MouseEvent.BUTTON3 -> 3;
      default -> 1;
    };
  }

  private static SwingSceneRendererRegistry createRendererRegistry(Engine engine) {
    SwingSceneRendererRegistry reg = new SwingSceneRendererRegistry();
    reg.register(Scene2D.class, (scene2D, ctx) -> {
      double w = ctx.width();
      double h = ctx.height();
      SwingBlitter2D bl = ctx.blitter();
      bl.clear(0, 0, 0, 1);
      if (scene2D instanceof Scene2DBase s2db) {
        if (engine != null) s2db.setInput(engine.input());
        if (s2db.getCamera() == null) s2db.setCamera(new Camera2D());
      }
      double targetW = (engine != null && engine.getConfig() != null) ? engine.getConfig().width() : w;
      double targetH = (engine != null && engine.getConfig() != null) ? engine.getConfig().height() : h;
      var vp = ViewportScaler2D.fit(targetW, targetH, w, h);
      bl.push();
      bl.translate(vp.offsetX(), vp.offsetY());
      bl.scale(vp.scale(), vp.scale());
      scene2D.render(bl, vp.targetWidth(), vp.targetHeight());
      bl.pop();
    });
    return reg;
  }

  private static void applyLinuxDefaultWindowState(JFrame frame) {
    if (frame == null || !isLinux()) return;
    frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
  }
}
