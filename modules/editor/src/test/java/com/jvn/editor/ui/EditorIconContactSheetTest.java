package com.jvn.editor.ui;

import com.jvn.fx.testkit.FxToolkit;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in atlas of the production editor icon factories, including every semantic enum role. */
class EditorIconContactSheetTest {
  interface Factory { Region create() throws Exception; }
  record Entry(String name, Factory factory) {}

  @Test void exportAllEditorIcons() throws Exception {
    String output = System.getenv().getOrDefault("JVN_EDITOR_ICON_ATLAS", "");
    assumeTrue(!output.isBlank(), "Set JVN_EDITOR_ICON_ATLAS to export contact sheets");
    assumeTrue(FxToolkit.ensureStarted());
    Path out = Path.of(output); Files.createDirectories(out);
    StringBuilder html = new StringBuilder("<!doctype html><meta charset='utf-8'><title>JVN editor icon atlas</title><style>body{background:#13191f;color:#e6eef6;font:16px system-ui;margin:32px}img{max-width:100%;display:block;margin-bottom:40px}a{color:#83cfff}</style><h1>JVN editor icon atlas</h1><p>Production artwork on dark and light backgrounds. Each cell shows a enlarged detail view and native-size rendering.</p>");
    StringBuilder manifest = new StringBuilder("family,role\n");
    Class<?>[] families = {AeroIcon.class, SidebarToolIcon.class, PuppeteerAeroIcon.class,
        VersionControlIcon.class, DiagnosticsToolbarIcon.class, RuntimeConsoleIcon.class,
        LayeredVisualizerIcon.class, CodeDockerIcon.class, PanelChooserActionIcon.class};
    for (Class<?> family : families) {
      List<Entry> entries = new ArrayList<>();
      Class<?> kind = Arrays.stream(family.getDeclaredClasses()).filter(c -> c.isEnum() && c.getSimpleName().equals("Kind")).findFirst().orElseThrow();
      Method factory = Arrays.stream(family.getDeclaredMethods()).filter(m -> m.getName().equals("of") && m.getParameterCount()>=1 && m.getParameterTypes()[0]==kind && (m.getParameterCount()==1 || (m.getParameterCount()==2 && m.getParameterTypes()[1]==double.class))).sorted(Comparator.comparingInt(Method::getParameterCount)).findFirst().orElseThrow();
      factory.setAccessible(true);
      for (Object role : kind.getEnumConstants()) entries.add(new Entry(role.toString(), () -> (Region) (factory.getParameterCount()==1 ? factory.invoke(null, role) : factory.invoke(null, role, 24.0))));
      export(out, family.getSimpleName(), entries, html, manifest);
    }
    List<Entry> compact = new ArrayList<>();
    for (Method method : Arrays.stream(CssIcon.class.getDeclaredMethods()).sorted(Comparator.comparing(Method::getName)).toList()) {
      if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()) && method.getParameterCount()==0 && Region.class.isAssignableFrom(method.getReturnType()))
        compact.add(new Entry(method.getName(), () -> (Region) method.invoke(null)));
    }
    for (String name : List.of("help", "info", "questionMark", "wrench")) {
      Method method = CssIcon.class.getMethod(name, String.class);
      compact.add(new Entry(name, () -> (Region) method.invoke(null, "#b0b8c8")));
    }
    compact.sort(Comparator.comparing(Entry::name));
    export(out, "CssIcon", compact, html, manifest);
    List<Entry> files = new ArrayList<>();
    for (var kind : ProjectFileIcons.Kind.values()) files.add(new Entry(kind.name(), () -> ProjectFileIcons.iconFor(kind, 24)));
    export(out,"ProjectFileIcons",files,html,manifest);
    export(out,"SharedControls",List.of(new Entry("Refresh", () -> RefreshIcon.of(24)), new Entry("NewTab", () -> NewTabIcon.of(24)), new Entry("Alert", () -> AlertIcon.of("#efb34d",24))),html,manifest);
    Files.writeString(out.resolve("index.html"),html);
    Files.writeString(out.resolve("inventory.csv"),manifest);
  }

  private static void export(Path out, String family, List<Entry> entries, StringBuilder html, StringBuilder manifest) throws Exception {
    int columns=8, cellW=170, cellH=150, rows=(entries.size()+columns-1)/columns;
    BufferedImage sheet=new BufferedImage(columns*cellW,72+rows*cellH,BufferedImage.TYPE_INT_RGB);
    Graphics2D g=sheet.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setColor(new Color(19,25,31));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
    g.setColor(new Color(227,239,249));g.setFont(new Font("SansSerif",Font.BOLD,24));g.drawString(family+"  /  "+entries.size()+" roles",20,40);
    for(int i=0;i<entries.size();i++) {
      Entry entry=entries.get(i);int x=(i%columns)*cellW,y=72+(i/columns)*cellH;
      for(int theme=0;theme<2;theme++) {
        final boolean light=theme==1;
        BufferedImage icon=FxToolkit.runFx(() -> {
          Region node=entry.factory().create();
          StackPane root=new StackPane(node); root.setStyle("-fx-background-color:"+(light?"#edf2f7":"#202932")+";");
          new Scene(root,48,48);root.applyCss();root.layout();
          javafx.scene.SnapshotParameters parameters = new javafx.scene.SnapshotParameters();
          parameters.setTransform(javafx.scene.transform.Transform.scale(2,2));
          return SwingFXUtils.fromFXImage(root.snapshot(parameters,null),null);
        });
        g.drawImage(icon,x+5+theme*82,y+2,76,76,null);
        g.drawImage(icon,x+19+theme*82,y+77,48,48,null);
      }
      g.setFont(new Font("SansSerif",Font.PLAIN,10));g.setColor(new Color(198,211,223));
      g.drawString(entry.name(),x+5,y+144);
      manifest.append(family).append(',').append(entry.name()).append('\n');
    }
    g.dispose();ImageIO.write(sheet,"png",out.resolve(family+".png").toFile());
    html.append("<h2>").append(family).append("</h2><a href='").append(family).append(".png'><img src='").append(family).append(".png'></a>");
  }
}
