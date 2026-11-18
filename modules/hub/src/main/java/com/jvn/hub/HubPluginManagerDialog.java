package com.jvn.hub;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/** Mature, code-free plugin bundle manager launched by the Engine Hub. */
final class HubPluginManagerDialog extends JDialog {
  private static final Color BG = Color.decode("#101010");
  private static final Color PANEL = JvnHub.PANEL_BG;
  private static final Color RAISED = JvnHub.PANEL_BG_TOP;
  private static final Color SELECTED = JvnHub.SELECTION_BG;
  private static final Color BORDER = JvnHub.BORDER_NEUTRAL;
  private static final Color TEXT = Color.decode("#f0f0f0");
  private static final Color SOFT = Color.decode("#c5c5c5");
  private static final Color MUTED = Color.decode("#9a9a9a");
  private static final Color ACCENT = Color.decode("#b987ff");
  private static final Color PASS = Color.decode("#65d58b");
  private static final Color WARN = Color.decode("#f1b65c");
  private static final Color ERROR = Color.decode("#f07178");
  private static final DateTimeFormatter DATE = DateTimeFormatter
      .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
      .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

  private final Path userPlugins;
  private final Path projectPlugins;
  private final Runnable onPluginsChanged;
  private final HubPluginBundleVerifier verifier = new HubPluginBundleVerifier();
  private final List<PluginRow> allRows = new ArrayList<>();
  private final DefaultListModel<PluginRow> shownRows = new DefaultListModel<>();
  private final JList<PluginRow> list = new JList<>(shownRows);
  private final JTextField search = new JTextField();
  private final JComboBox<String> scope = new JComboBox<>();
  private final JComboBox<String> health = new JComboBox<>(new String[] {
      "Any status", "Verified", "Disabled", "Needs attention"});
  private final JLabel visibleCount = label("0 plugins", MUTED, 11, false);
  private final JLabel status = label("Scanning plugin folders…", MUTED, 11, false);
  private final JButton refreshButton = button("Refresh", HubIcon.Kind.REFRESH, false, this::refresh);
  private final CardLayout detailsLayout = new CardLayout();
  private final JPanel details = new JPanel(detailsLayout);
  private final JLabel detailName = label("", TEXT, 18, true);
  private final JLabel detailVersion = label("", MUTED, 12, false);
  private final JLabel detailState = label("", TEXT, 10, true);
  private final JTextArea description = textArea();
  private final JTextArea diagnostics = textArea();
  private final JPanel facts = new JPanel(new GridBagLayout());
  private final JButton toggle = button("Disable", HubIcon.Kind.STOP, false, this::toggleSelected);
  private final JButton reveal = button("Show in Folder", HubIcon.Kind.FOLDER, false, this::revealSelected);
  private final JButton copy = button("Copy Details", HubIcon.Kind.COPY, false, this::copySelected);
  private final JButton remove = button("Move to Trash", HubIcon.Kind.TRASH, false, this::removeSelected);
  private final CardLayout inventoryLayout = new CardLayout();
  private final JPanel inventoryCards = new JPanel(inventoryLayout);
  private final JLabel emptyTitle = label("Your plugin library is empty", TEXT, 15, true);
  private final JLabel emptyHint = label("Install a plugin JAR to add tools to JVN.", MUTED, 12, false);
  private int scanGeneration;
  private Notice postScanNotice;

  HubPluginManagerDialog(Window owner, Path projectRoot, Runnable onPluginsChanged) {
    this(owner, projectRoot, Path.of(System.getProperty("user.home", "."), ".jvn", "plugins"), onPluginsChanged);
  }

  HubPluginManagerDialog(Window owner, Path projectRoot, Path userPlugins, Runnable onPluginsChanged) {
    super(owner, "JVN Plugin Manager", ModalityType.MODELESS);
    this.userPlugins = userPlugins;
    projectPlugins = projectRoot == null ? null : projectRoot.resolve("plugins");
    this.onPluginsChanged = onPluginsChanged == null ? () -> { } : onPluginsChanged;
    buildUi();
    bind();
    refresh();
  }

  private void buildUi() {
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(1040, 680));
    setSize(1180, 760);
    JPanel root = panel(new BorderLayout(), BG);
    root.add(header(), BorderLayout.NORTH);
    root.add(workspace(), BorderLayout.CENTER);
    root.add(footer(), BorderLayout.SOUTH);
    setContentPane(root);
    setLocationRelativeTo(getOwner());
  }

  private JComponent header() {
    JPanel bar = panel(new BorderLayout(16, 0), RAISED);
    bar.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), empty(17, 21, 17, 21)));
    bar.add(new JLabel(new JvnHub.JvnLogoIcon(110, 58)), BorderLayout.WEST);

    JPanel titles = vertical(RAISED);
    titles.add(label("Plugin Manager", TEXT, 19, true));
    titles.add(Box.createVerticalStrut(3));
    titles.add(label("ENGINE HUB  /  EXTENSIONS", MUTED, 10, true));
    bar.add(titles, BorderLayout.CENTER);
    return bar;
  }

  private JComponent workspace() {
    JPanel root = panel(new BorderLayout(), BG);
    root.setBorder(empty(16, 18, 12, 18));
    JPanel inventory = panel(new BorderLayout(0, 10), BG);
    inventory.setBorder(empty(0, 0, 0, 10));
    inventory.add(filters(), BorderLayout.NORTH);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new PluginRenderer());
    list.setFixedCellHeight(76);
    list.setBackground(PANEL);
    list.setForeground(TEXT);
    list.setBorder(empty(4, 4, 4, 4));
    inventoryCards.add(scroller(list, false), "list");
    JPanel emptyLibrary = panel(new GridBagLayout(), PANEL);
    JPanel emptyContent = vertical(PANEL);
    emptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
    emptyHint.setAlignmentX(Component.CENTER_ALIGNMENT);
    emptyContent.add(emptyTitle);
    emptyContent.add(Box.createVerticalStrut(8));
    emptyContent.add(emptyHint);
    emptyContent.add(Box.createVerticalStrut(20));
    JButton install = button("Install a plugin…", HubIcon.Kind.INSTALL, true, () -> installInto(userPlugins, "User"));
    install.setAlignmentX(Component.CENTER_ALIGNMENT);
    emptyContent.add(install);
    emptyLibrary.add(emptyContent);
    inventoryCards.add(emptyLibrary, "empty");
    inventoryCards.setBorder(BorderFactory.createLineBorder(BORDER));
    inventory.add(inventoryCards, BorderLayout.CENTER);
    JPanel listBottom = panel(new BorderLayout(), BG);
    listBottom.add(visibleCount, BorderLayout.WEST);
    listBottom.add(refreshButton, BorderLayout.EAST);
    inventory.add(listBottom, BorderLayout.SOUTH);
    buildDetails();
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inventory, details);
    split.setUI(new BasicSplitPaneUI() {
      @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
        return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
          @Override public void paint(Graphics g) {
            g.setColor(BG);
            g.fillRect(0, 0, getWidth(), getHeight());
          }
        };
      }
    });
    split.setBorder(null);
    split.setOpaque(false);
    split.setResizeWeight(.4);
    split.setDividerLocation(440);
    split.setDividerSize(14);
    root.add(split);
    return root;
  }

  private JComponent filters() {
    JPanel root = panel(new BorderLayout(7, 8), BG);
    root.add(label("Installed plugins", TEXT, 14, true), BorderLayout.NORTH);
    search.setBackground(Color.decode("#151515"));
    search.setForeground(TEXT);
    search.setCaretColor(TEXT);
    search.setSelectionColor(Color.decode("#5c4278"));
    search.setToolTipText("Search by name, ID, vendor, filename, or description");
    search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), empty(7, 9, 7, 9)));
    search.setFont(search.getFont().deriveFont(Font.PLAIN, 12f));
    search.putClientProperty("JTextField.placeholderText", "Search plugins");
    JPanel searchRow = panel(new BorderLayout(10, 0), BG);
    searchRow.add(label("Search", MUTED, 11, false), BorderLayout.WEST);
    searchRow.add(search);
    root.add(searchRow, BorderLayout.CENTER);
    scope.setModel(new DefaultComboBoxModel<>(projectPlugins == null
        ? new String[] {"All locations", "User"} : new String[] {"All locations", "User", "Project"}));
    styleCombo(scope, 160);
    styleCombo(health, 160);
    JPanel combos = panel(new FlowLayout(FlowLayout.LEFT, 7, 0), BG);
    combos.add(scope);
    combos.add(health);
    root.add(combos, BorderLayout.SOUTH);
    return root;
  }

  private void buildDetails() {
    details.setBackground(PANEL);
    details.setBorder(BorderFactory.createLineBorder(BORDER));
    JPanel empty = panel(new GridBagLayout(), PANEL);
    JPanel message = vertical(PANEL);
    JLabel title = label("Select a plugin", SOFT, 16, true);
    title.setAlignmentX(Component.CENTER_ALIGNMENT);
    JLabel hint = label("Choose an installed plugin to view its details.", MUTED, 12, false);
    hint.setAlignmentX(Component.CENTER_ALIGNMENT);
    message.add(title);
    message.add(Box.createVerticalStrut(7));
    message.add(hint);
    message.add(Box.createVerticalStrut(26));
    JLabel note = label("Versions  ·  Capabilities  ·  Health checks", MUTED, 11, false);
    note.setAlignmentX(Component.CENTER_ALIGNMENT);
    message.add(note);
    empty.add(message);
    details.add(empty, "empty");

    JPanel selected = panel(new BorderLayout(0, 13), PANEL);
    selected.setBorder(empty(18, 18, 16, 18));
    JPanel heading = panel(new BorderLayout(8, 0), PANEL);
    JPanel names = vertical(PANEL);
    names.add(detailName);
    names.add(Box.createVerticalStrut(3));
    names.add(detailVersion);
    heading.add(names);
    detailState.setOpaque(true);
    detailState.setBorder(empty(5, 9, 5, 9));
    heading.add(detailState, BorderLayout.EAST);
    selected.add(heading, BorderLayout.NORTH);

    JPanel body = new DetailBody();
    body.add(section("ABOUT"));
    body.add(Box.createVerticalStrut(5));
    description.setRows(3);
    description.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
    body.add(description);
    body.add(Box.createVerticalStrut(13));
    body.add(section("MANIFEST & FILE"));
    body.add(Box.createVerticalStrut(5));
    facts.setBackground(PANEL);
    facts.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(facts);
    body.add(Box.createVerticalStrut(13));
    body.add(section("DIAGNOSTICS"));
    body.add(Box.createVerticalStrut(5));
    JScrollPane diagnosticScroll = scroller(diagnostics, true);
    diagnosticScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
    diagnosticScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(diagnosticScroll);
    body.add(Box.createVerticalGlue());
    JScrollPane bodyScroll = scroller(body, false);
    bodyScroll.setBorder(null);
    bodyScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    selected.add(bodyScroll);
    JPanel actions = panel(new GridLayout(2, 2, 8, 8), PANEL);
    actions.add(toggle);
    actions.add(reveal);
    actions.add(copy);
    actions.add(remove);
    selected.add(actions, BorderLayout.SOUTH);
    details.add(selected, "selected");
    detailsLayout.show(details, "empty");
  }

  private JComponent footer() {
    JPanel root = panel(new BorderLayout(12, 0), RAISED);
    root.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER), empty(12, 18, 12, 18)));
    status.setBorder(empty(0, 0, 10, 0));
    root.add(status, BorderLayout.NORTH);
    JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 7, 0), RAISED);
    actions.add(button("Open User Folder", HubIcon.Kind.FOLDER, false, () -> openFolder(userPlugins)));
    if (projectPlugins != null) actions.add(button("Open Project Folder", HubIcon.Kind.FOLDER, false, () -> openFolder(projectPlugins)));
    actions.add(button("Install for User…", HubIcon.Kind.INSTALL, true, () -> installInto(userPlugins, "User")));
    if (projectPlugins != null) actions.add(button("Install for Project…", HubIcon.Kind.INSTALL, true, () -> installInto(projectPlugins, "Project")));
    actions.add(button("Close", HubIcon.Kind.CLOSE, false, this::dispose));
    root.add(actions, BorderLayout.CENTER);
    return root;
  }

  private void bind() {
    list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showDetails(); });
    search.getDocument().addDocumentListener((DocumentListenerAdapter) e -> filter());
    scope.addActionListener(e -> filter());
    health.addActionListener(e -> filter());
    getRootPane().registerKeyboardAction(e -> refresh(), KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW);
    getRootPane().registerKeyboardAction(e -> { search.requestFocusInWindow(); search.selectAll(); },
        KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
        JComponent.WHEN_IN_FOCUSED_WINDOW);
    list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "trash");
    list.getActionMap().put("trash", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { removeSelected(); }
    });
  }

  private void refresh() {
    int generation = ++scanGeneration;
    Path selected = list.getSelectedValue() == null ? null : list.getSelectedValue().source();
    refreshButton.setEnabled(false);
    setStatus("Verifying installed bundles…", MUTED);
    new SwingWorker<Snapshot, Void>() {
      @Override protected Snapshot doInBackground() {
        List<PluginRow> found = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        scan(userPlugins, "User", found, errors);
        if (projectPlugins != null) scan(projectPlugins, "Project", found, errors);
        found.sort(Comparator.comparingInt((PluginRow r) -> r.scope().equals("User") ? 0 : 1)
            .thenComparing(r -> r.name().toLowerCase(Locale.ROOT)));
        return new Snapshot(addDiagnostics(found), errors);
      }
      @Override protected void done() {
        if (generation != scanGeneration || !isDisplayable()) return;
        refreshButton.setEnabled(true);
        try {
          Snapshot snapshot = get();
          allRows.clear();
          allRows.addAll(snapshot.rows());
          filter();
          select(selected);
          updateSummary(snapshot.errors());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          setStatus("Plugin scan was interrupted.", ERROR);
        } catch (Exception e) {
          setStatus("Plugin scan failed: " + errorText(e.getCause()), ERROR);
        }
      }
    }.execute();
  }

  private void scan(Path directory, String location, List<PluginRow> found, List<String> errors) {
    if (directory == null || !Files.isDirectory(directory)) return;
    try (Stream<Path> files = Files.list(directory)) {
      for (Path file : files.filter(Files::isRegularFile).filter(HubPluginManagerDialog::isBundle).sorted().toList()) {
        boolean enabled = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
        found.add(new PluginRow(location, file, enabled, verifier.verify(file), List.of()));
      }
    } catch (IOException e) {
      errors.add(location + " folder: " + errorText(e));
    }
  }

  private static List<PluginRow> addDiagnostics(List<PluginRow> rows) {
    Map<String, List<PluginRow>> byId = new LinkedHashMap<>();
    for (PluginRow row : rows) if (row.enabled() && row.verification().isValid()) {
      byId.computeIfAbsent(row.verification().descriptor().id(), ignored -> new ArrayList<>()).add(row);
    }
    List<PluginRow> result = new ArrayList<>();
    for (PluginRow row : rows) {
      List<String> notes = new ArrayList<>();
      Descriptor descriptor = row.verification().descriptor();
      if (!row.verification().isValid()) notes.add(row.verification().error());
      else if (row.enabled()) {
        List<PluginRow> duplicates = byId.getOrDefault(descriptor.id(), List.of());
        if (duplicates.size() > 1) notes.add("Duplicate enabled plugin ID. The runtime will use the first discovered copy and ignore the other.");
        for (Dependency dependency : descriptor.dependencies()) if (!byId.containsKey(dependency.id())) {
          notes.add("Missing enabled dependency: " + dependency.display());
        }
      }
      result.add(new PluginRow(row.scope(), row.source(), row.enabled(), row.verification(), List.copyOf(notes)));
    }
    return List.copyOf(result);
  }

  private void filter() {
    Path selected = list.getSelectedValue() == null ? null : list.getSelectedValue().source();
    String query = search.getText().strip().toLowerCase(Locale.ROOT);
    String location = Objects.toString(scope.getSelectedItem(), "All locations");
    String state = Objects.toString(health.getSelectedItem(), "Any status");
    shownRows.clear();
    for (PluginRow row : allRows) {
      if (!location.equals("All locations") && !location.equals(row.scope())) continue;
      if (state.equals("Verified") && row.kind() != Kind.VERIFIED) continue;
      if (state.equals("Disabled") && row.kind() != Kind.DISABLED) continue;
      if (state.equals("Needs attention") && row.kind() != Kind.ISSUE) continue;
      if (!query.isBlank() && !row.searchText().contains(query)) continue;
      shownRows.addElement(row);
    }
    long enabled = allRows.stream().filter(PluginRow::enabled).count();
    long issues = allRows.stream().filter(row -> row.kind() == Kind.ISSUE).count();
    visibleCount.setText(shownRows.size() + " of " + allRows.size() + " plugins · " + enabled + " enabled"
        + (issues > 0 ? " · " + issues + " need attention" : ""));
    emptyTitle.setText(allRows.isEmpty() ? "Your plugin library is empty" : "No matching plugins");
    emptyHint.setText(allRows.isEmpty() ? "Install a plugin JAR to add tools to JVN." : "Try a different search or change the filters.");
    inventoryLayout.show(inventoryCards, shownRows.isEmpty() ? "empty" : "list");
    select(selected);
    if (list.getSelectedIndex() < 0) showDetails();
  }

  private void updateSummary(List<String> errors) {
    long issues = allRows.stream().filter(r -> r.kind() == Kind.ISSUE).count();
    if (postScanNotice != null) {
      setStatus(postScanNotice.message(), postScanNotice.color());
      postScanNotice = null;
    } else if (!errors.isEmpty()) setStatus(String.join(" · ", errors), ERROR);
    else if (allRows.isEmpty()) setStatus("No plugins installed. Install a verified JAR to get started.", MUTED);
    else if (issues > 0) setStatus(allRows.size() + " bundles inspected · " + issues + " need attention", WARN);
    else setStatus(allRows.size() + " bundles inspected · all enabled plugins look healthy", PASS);
  }

  private void showDetails() {
    PluginRow row = list.getSelectedValue();
    if (row == null) { detailsLayout.show(details, "empty"); return; }
    Descriptor descriptor = row.verification().descriptor();
    detailName.setText(row.name());
    detailVersion.setText(descriptor == null ? row.source().getFileName().toString()
        : "Version " + descriptor.version() + "  ·  " + row.scope() + " plugin");
    detailState.setText(row.kind().label.toUpperCase(Locale.ROOT));
    detailState.setForeground(row.kind().color);
    detailState.setBackground(dark(row.kind().color));
    description.setText(descriptor == null || descriptor.description().isBlank()
        ? "No description is available in this plugin manifest." : descriptor.description());
    description.setCaretPosition(0);
    facts.removeAll();
    int line = 0;
    line = fact("Plugin ID", descriptor == null ? "Unavailable" : descriptor.id(), line);
    line = fact("Vendor", descriptor == null || descriptor.vendor().isBlank() ? "Not specified" : descriptor.vendor(), line);
    line = fact("JVN API", descriptor == null ? "Unavailable" : descriptor.jvnApi(), line);
    line = fact("Entrypoint", descriptor == null ? "Unavailable" : descriptor.entrypoint(), line);
    line = fact("Capabilities", descriptor == null || descriptor.capabilities().isEmpty()
        ? "None declared" : String.join(", ", descriptor.capabilities()), line);
    line = fact("Dependencies", descriptor == null || descriptor.dependencies().isEmpty() ? "None"
        : descriptor.dependencies().stream().map(Dependency::display).reduce((a, b) -> a + ", " + b).orElse("None"), line);
    line = fact("File", row.source().getFileName().toString(), line);
    line = fact("Size", bytes(row.verification().size()), line);
    line = fact("Modified", row.verification().modified() == null ? "Unknown" : DATE.format(row.verification().modified()), line);
    fact("SHA-256", shortHash(row.verification().sha256()), line);
    facts.revalidate();
    facts.repaint();
    String note = row.notes().isEmpty() ? (row.enabled()
        ? "✓ Manifest, API range, and entrypoint structure verified."
        : "ℹ This bundle is disabled and will not be discovered by the runtime.")
        : row.notes().stream().map(n -> "• " + n).reduce((a, b) -> a + "\n" + b).orElse("");
    diagnostics.setForeground(row.notes().isEmpty() ? PASS : WARN);
    diagnostics.setText(note);
    diagnostics.setCaretPosition(0);
    toggle.setText(row.enabled() ? "Disable" : "Enable");
    toggle.setIcon(JvnHub.uiIcon(row.enabled() ? HubIcon.Kind.STOP : HubIcon.Kind.PLAY, 16));
    toggle.setEnabled(row.verification().isValid());
    detailsLayout.show(details, "selected");
  }

  private int fact(String name, String value, int row) {
    GridBagConstraints left = constraints(0, row, 0, GridBagConstraints.NONE);
    left.insets = new Insets(3, 0, 3, 13);
    facts.add(label(name, MUTED, 11, false), left);
    GridBagConstraints right = constraints(1, row, 1, GridBagConstraints.HORIZONTAL);
    right.insets = new Insets(3, 0, 3, 0);
    JLabel content = label(value == null || value.isBlank() ? "—" : value, SOFT, 11, false);
    content.setToolTipText(value);
    content.setMinimumSize(new Dimension(0, content.getPreferredSize().height));
    facts.add(content, right);
    return row + 1;
  }

  private void installInto(Path destination, String location) {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Install JVN plugin for " + location.toLowerCase(Locale.ROOT));
    chooser.setAcceptAllFileFilterUsed(false);
    chooser.setFileFilter(new FileNameExtensionFilter("JVN plugin bundles (*.jar)", "jar"));
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
    Path source = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
    Verification verification = verifier.verify(source);
    if (!verification.isValid()) { error("Plugin verification failed", verification.error()); return; }
    Descriptor descriptor = verification.descriptor();
    PluginRow existing = allRows.stream().filter(r -> r.verification().descriptor() != null)
        .filter(r -> Objects.equals(r.source().getParent(), destination.toAbsolutePath().normalize()))
        .filter(r -> r.verification().descriptor().id().equals(descriptor.id())).findFirst().orElse(null);
    Path target = existing == null ? destination.resolve(source.getFileName()) : existing.source();
    String prompt = existing == null && !Files.exists(target) ? "Install" : "Replace the existing copy of";
    if (JOptionPane.showConfirmDialog(this, prompt + " “" + descriptor.name() + "” " + descriptor.version()
        + " for " + location.toLowerCase(Locale.ROOT) + "?\n\nPlugin ID: " + descriptor.id(),
        "Confirm plugin installation", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
    Path temporary = null;
    try {
      Files.createDirectories(destination);
      if (Files.exists(target) && Files.isSameFile(source, target)) {
        setStatus(descriptor.name() + " is already installed at that location.", MUTED);
        return;
      }
      temporary = Files.createTempFile(destination, ".jvn-plugin-install-", ".tmp");
      Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
      Verification staged = verifier.verifyArchive(temporary);
      if (!staged.isValid()) throw new IOException("Staged plugin failed verification: " + staged.error());
      move(temporary, target);
      temporary = null;
      changed("Installed " + descriptor.name() + " " + descriptor.version() + ". Reload the editor project to activate it.", PASS);
    } catch (IOException e) {
      error("Could not install plugin", errorText(e));
    } finally {
      if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
    }
  }

  private void toggleSelected() {
    PluginRow row = list.getSelectedValue();
    if (row == null || !row.verification().isValid()) return;
    String name = row.source().getFileName().toString();
    Path target = row.enabled() ? row.source().resolveSibling(name + ".disabled")
        : row.source().resolveSibling(name.substring(0, name.length() - ".disabled".length()));
    if (Files.exists(target) && JOptionPane.showConfirmDialog(this, "Replace the existing file?\n" + target.getFileName(),
        "Plugin file already exists", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
    try {
      move(row.source(), target);
      changed((row.enabled() ? "Disabled " : "Enabled ") + row.name()
          + ". Reload the editor project to apply the change.", row.enabled() ? MUTED : PASS);
    } catch (IOException e) { error("Could not " + (row.enabled() ? "disable" : "enable") + " plugin", errorText(e)); }
  }

  private void removeSelected() {
    PluginRow row = list.getSelectedValue();
    if (row == null) return;
    boolean trash = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    String question = trash ? "Move this plugin bundle to the trash?" : "Permanently delete this plugin bundle?";
    if (JOptionPane.showConfirmDialog(this, question + "\n\n" + row.source().getFileName(),
        trash ? "Move plugin to trash" : "Delete plugin", JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
    try {
      boolean removed = trash ? Desktop.getDesktop().moveToTrash(row.source().toFile()) : Files.deleteIfExists(row.source());
      if (!removed) throw new IOException("The operating system did not remove the file");
      changed((trash ? "Moved " : "Deleted ") + row.name() + (trash ? " to the trash. " : ". ")
          + "Reload the editor project to apply the change.", MUTED);
    } catch (IOException | UnsupportedOperationException e) { error("Could not remove plugin", errorText(e)); }
  }

  private void revealSelected() {
    PluginRow row = list.getSelectedValue();
    if (row == null) return;
    try {
      if (!Desktop.isDesktopSupported()) throw new IOException("Desktop integration is not available");
      Desktop desktop = Desktop.getDesktop();
      if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(row.source().toFile());
      else desktop.open(row.source().getParent().toFile());
    } catch (IOException | UnsupportedOperationException e) { error("Could not show plugin file", errorText(e)); }
  }

  private void copySelected() {
    PluginRow row = list.getSelectedValue();
    if (row == null) return;
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(row.report()), null);
    setStatus("Copied plugin details to the clipboard.", PASS);
  }

  private void openFolder(Path folder) {
    try {
      Files.createDirectories(folder);
      if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
        throw new IOException("Desktop folder opening is not available");
      Desktop.getDesktop().open(folder.toFile());
    } catch (IOException | UnsupportedOperationException e) { error("Could not open plugin folder", errorText(e)); }
  }

  private void changed(String message, Color color) {
    onPluginsChanged.run();
    postScanNotice = new Notice(message, color);
    refresh();
  }

  private void select(Path path) {
    if (path == null) return;
    for (int i = 0; i < shownRows.size(); i++) if (path.equals(shownRows.get(i).source())) {
      list.setSelectedIndex(i);
      list.ensureIndexIsVisible(i);
      return;
    }
  }

  private void setStatus(String message, Color color) { status.setText(message); status.setForeground(color); }
  private void error(String title, String message) {
    setStatus(title + ": " + message, ERROR);
    JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
  }

  private static boolean isBundle(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".jar") || name.endsWith(".jar.disabled");
  }

  private static void move(Path source, Path target) throws IOException {
    try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
    catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
  }

  private static String errorText(Throwable error) {
    if (error == null) return "Unknown error";
    String message = error.getMessage();
    return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
  }

  private static String bytes(long value) {
    if (value < 0) return "Unknown";
    if (value < 1024) return value + " B";
    double size = value;
    String[] units = {"KB", "MB", "GB"};
    int unit = -1;
    do { size /= 1024; unit++; } while (size >= 1024 && unit < units.length - 1);
    return String.format(Locale.ROOT, size >= 10 ? "%.1f %s" : "%.2f %s", size, units[unit]);
  }

  private static String shortHash(String hash) {
    return hash == null || hash.isBlank() ? "Unavailable" : hash.substring(0, 12) + "…" + hash.substring(hash.length() - 12);
  }

  private static JPanel panel(LayoutManager layout, Color color) { JPanel p = new JPanel(layout); p.setBackground(color); return p; }
  private static JPanel vertical(Color color) { JPanel p = panel(null, color); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p; }
  private static javax.swing.border.Border empty(int t, int l, int b, int r) { return BorderFactory.createEmptyBorder(t, l, b, r); }
  private static JLabel label(String text, Color color, float size, boolean bold) {
    JLabel l = new JLabel(text); l.setForeground(color); l.setFont(l.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, size)); return l;
  }
  private static JLabel section(String text) { JLabel l = label(text, ACCENT, 10, true); l.setAlignmentX(Component.LEFT_ALIGNMENT); return l; }
  private static JTextArea textArea() {
    JTextArea a = new JTextArea(); a.setEditable(false); a.setLineWrap(true); a.setWrapStyleWord(true);
    a.setAlignmentX(Component.LEFT_ALIGNMENT);
    a.setOpaque(false); a.setForeground(SOFT); a.setFont(a.getFont().deriveFont(11f)); a.setBorder(empty(2, 0, 2, 0)); return a;
  }
  private static JScrollPane scroller(Component view, boolean border) {
    JScrollPane s = new JScrollPane(view); s.setBackground(PANEL); s.getViewport().setBackground(PANEL);
    s.setBorder(border ? BorderFactory.createLineBorder(BORDER) : null); JvnHub.styleScrollBar(s.getVerticalScrollBar());
    JvnHub.styleScrollBar(s.getHorizontalScrollBar());
    return s;
  }
  private static void styleCombo(JComboBox<String> combo, int width) {
    JvnHub.styleHubComboBox(combo, new Dimension(width, 34));
  }
  private static JButton button(String text, HubIcon.Kind kind, boolean primary, Runnable action) {
    JButton button = new JvnHub.FlatButton(text, JvnHub.uiIcon(kind, 16), primary ? ACCENT : null);
    button.addActionListener(e -> action.run());
    return button;
  }
  private static GridBagConstraints constraints(int x, int y, double weight, int fill) {
    GridBagConstraints c = new GridBagConstraints(); c.gridx = x; c.gridy = y; c.weightx = weight;
    c.fill = fill; c.anchor = GridBagConstraints.NORTHWEST; return c;
  }
  private static Color dark(Color color) { return new Color(Math.max(18, color.getRed() / 4), Math.max(18, color.getGreen() / 4), Math.max(18, color.getBlue() / 4)); }

  private static final class DetailBody extends JPanel implements Scrollable {
    DetailBody() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setBackground(PANEL);
    }
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
      return Math.max(16, visible.height - 16);
    }
  }

  private final class PluginRenderer implements ListCellRenderer<PluginRow> {
    @Override public Component getListCellRendererComponent(JList<? extends PluginRow> source, PluginRow row,
        int index, boolean selected, boolean focus) {
      JPanel card = panel(new BorderLayout(10, 0), selected ? SELECTED : PANEL);
      card.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createMatteBorder(0, selected ? 3 : 0, 1, 0, selected ? JvnHub.FOCUS_RING : BORDER),
          empty(12, selected ? 9 : 12, 12, 12)));
      JLabel icon = new JLabel(JvnHub.uiIcon(row.kind().icon, 30), SwingConstants.CENTER);
      icon.setPreferredSize(new Dimension(icon.getIcon().getIconWidth() + 4, 38));
      card.add(icon, BorderLayout.WEST);
      JPanel names = vertical(card.getBackground());
      names.add(label(row.name() + (row.version().isBlank() ? "" : "  " + row.version()), TEXT, 12, true));
      names.add(Box.createVerticalStrut(5));
      names.add(label(row.scope() + "  ·  " + row.id(), MUTED, 10, false));
      card.add(names);
      card.add(label(row.kind().label, row.kind().color, 10, true), BorderLayout.EAST);
      return card;
    }
  }

  private enum Kind {
    VERIFIED("Verified", HubIcon.Kind.PLUGIN, PASS),
    DISABLED("Disabled", HubIcon.Kind.PLUGIN_DISABLED, MUTED),
    ISSUE("Attention", HubIcon.Kind.PLUGIN_ALERT, WARN);

    final String label;
    final HubIcon.Kind icon;
    final Color color;

    Kind(String label, HubIcon.Kind icon, Color color) {
      this.label = label;
      this.icon = icon;
      this.color = color;
    }
  }

  private record PluginRow(String scope, Path source, boolean enabled, Verification verification, List<String> notes) {
    String name() { return verification.descriptor() == null ? source.getFileName().toString() : verification.descriptor().name(); }
    String version() { return verification.descriptor() == null ? "" : verification.descriptor().version(); }
    String id() { return verification.descriptor() == null ? "Manifest unavailable" : verification.descriptor().id(); }
    Kind kind() { return !verification.isValid() || !notes.isEmpty() ? Kind.ISSUE : enabled ? Kind.VERIFIED : Kind.DISABLED; }
    String searchText() {
      Descriptor d = verification.descriptor();
      return (source.getFileName() + " " + scope + " " + (d == null ? "" : d.id() + " " + d.name() + " " + d.vendor() + " " + d.description())).toLowerCase(Locale.ROOT);
    }
    String report() {
      Descriptor d = verification.descriptor();
      return "JVN Plugin Report\nName: " + name() + "\nVersion: " + version() + "\nID: " + id()
          + "\nState: " + kind().label + "\nScope: " + scope + "\nFile: " + source.toAbsolutePath()
          + "\nJVN API: " + (d == null ? "Unavailable" : d.jvnApi()) + "\nEntrypoint: "
          + (d == null ? "Unavailable" : d.entrypoint()) + "\nSHA-256: " + verification.sha256()
          + "\nDiagnostics: " + (notes.isEmpty() ? "None" : String.join(" | ", notes));
    }
  }
  private record Snapshot(List<PluginRow> rows, List<String> errors) { }
  private record Notice(String message, Color color) { }
  record Dependency(String id, String version) { String display() { return id + " " + version; } }
  record Descriptor(String id, String name, String version, String jvnApi, String entrypoint,
                    String description, String vendor, List<Dependency> dependencies, Set<String> capabilities) { }
  record Verification(Path source, Descriptor descriptor, String error, long size, Instant modified, String sha256) {
    boolean isValid() { return error == null || error.isBlank(); }
  }

  /** Structural preflight. It deliberately never loads a class from the bundle. */
  static final class HubPluginBundleVerifier {
    private static final int MAX_MANIFEST = 1024 * 1024;
    private static final Set<String> CAPABILITIES = Set.of(
        "script.command", "editor.tool", "asset.importer", "runtime.listener", "animation.easing");

    Verification verify(Path source) { return verify(source, true); }
    Verification verifyArchive(Path source) { return verify(source, false); }

    private Verification verify(Path source, boolean requireJarName) {
      Path jar = source == null ? null : source.toAbsolutePath().normalize();
      long size = size(jar); Instant modified = modified(jar); String hash = hash(jar);
      if (jar == null || !Files.isRegularFile(jar)) return failed(jar, null, "Plugin bundle is not a regular file", size, modified, hash);
      if (requireJarName && !isBundle(jar)) return failed(jar, null, "Plugin bundle must be a .jar file", size, modified, hash);
      try (JarFile archive = new JarFile(jar.toFile())) {
        var manifest = archive.getJarEntry("jvn-plugin.json");
        if (manifest == null || manifest.isDirectory()) return failed(jar, null, "Missing jvn-plugin.json", size, modified, hash);
        byte[] bytes;
        try (InputStream input = archive.getInputStream(manifest)) { bytes = input.readNBytes(MAX_MANIFEST + 1); }
        if (bytes.length > MAX_MANIFEST) throw new IOException("Plugin manifest exceeds the 1 MB safety limit");
        Object parsed = new Json(new String(bytes, StandardCharsets.UTF_8)).parse();
        if (!(parsed instanceof Map<?, ?> root)) throw new IOException("Plugin manifest must be a JSON object");
        Descriptor descriptor = descriptor(root);
        if (!acceptsVersion(descriptor.jvnApi(), "1.1.0")) return failed(jar, descriptor,
            "Requires JVN Plugin API " + descriptor.jvnApi() + "; this engine provides 1.1.0", size, modified, hash);
        var entrypoint = archive.getJarEntry(descriptor.entrypoint().replace('.', '/') + ".class");
        if (entrypoint == null || entrypoint.isDirectory()) return failed(jar, descriptor,
            "Entrypoint class is not present in the JAR", size, modified, hash);
        return new Verification(jar, descriptor, "", size, modified, hash);
      } catch (IOException | IllegalArgumentException e) {
        return failed(jar, null, errorText(e), size, modified, hash);
      }
    }

    private static Descriptor descriptor(Map<?, ?> root) throws IOException {
      String id = required(root, "id"), name = required(root, "name"), version = required(root, "version");
      String api = required(root, "jvnApi"), entrypoint = required(root, "entrypoint");
      String description = optional(root, "description"), vendor = optional(root, "vendor");
      List<Dependency> dependencies = new ArrayList<>();
      Object rawDependencies = root.get("dependencies");
      if (rawDependencies != null) {
        if (!(rawDependencies instanceof List<?> values)) throw new IOException("Manifest field 'dependencies' must be an array");
        for (Object value : values) {
          if (!(value instanceof Map<?, ?> dependency)) throw new IOException("Each plugin dependency must be an object");
          dependencies.add(new Dependency(required(dependency, "id"), dependency.containsKey("version") ? required(dependency, "version") : "*"));
        }
      }
      Set<String> capabilities = new LinkedHashSet<>();
      Object rawCapabilities = root.get("capabilities");
      if (rawCapabilities != null) {
        if (!(rawCapabilities instanceof List<?> values)) throw new IOException("Manifest field 'capabilities' must be an array");
        for (Object value : values) {
          if (!(value instanceof String capability) || !CAPABILITIES.contains(capability))
            throw new IOException("Unknown plugin capability: " + value);
          capabilities.add(capability);
        }
      }
      return new Descriptor(id, name, version, api, entrypoint, description, vendor, List.copyOf(dependencies), Set.copyOf(capabilities));
    }

    private static String required(Map<?, ?> root, String key) throws IOException {
      Object value = root.get(key);
      if (!(value instanceof String text) || text.isBlank()) throw new IOException("Plugin manifest field '" + key + "' is required");
      return text.strip();
    }
    private static String optional(Map<?, ?> root, String key) throws IOException {
      Object value = root.get(key); if (value == null) return "";
      if (!(value instanceof String text)) throw new IOException("Plugin manifest field '" + key + "' must be a string"); return text.strip();
    }
    private static boolean acceptsVersion(String range, String version) {
      if (range == null || range.isBlank() || "*".equals(range.trim())) return true;
      int[] actual = parseVersion(version);
      for (String clause : range.trim().split("\\s+")) {
        if (clause.endsWith(".x") || clause.endsWith(".*")) {
          String prefix = clause.substring(0, clause.length() - 2);
          int[] expected = parseVersion(prefix);
          int parts = prefix.split("\\.").length;
          if (actual[0] != expected[0] || (parts > 1 && actual[1] != expected[1])) return false;
        } else if (clause.startsWith("^")) {
          int[] expected = parseVersion(clause.substring(1));
          if (compareVersions(actual, expected) < 0 || actual[0] != expected[0]) return false;
        } else {
          String operator = clause.startsWith(">=") || clause.startsWith("<=") ? clause.substring(0, 2)
              : clause.startsWith(">") || clause.startsWith("<") || clause.startsWith("=")
                  ? clause.substring(0, 1) : "=";
          String operand = operator.equals("=") && !clause.startsWith("=") ? clause : clause.substring(operator.length());
          int comparison = compareVersions(actual, parseVersion(operand));
          if ((operator.equals(">=") && comparison < 0) || (operator.equals("<=") && comparison > 0)
              || (operator.equals(">") && comparison <= 0) || (operator.equals("<") && comparison >= 0)
              || (operator.equals("=") && comparison != 0)) return false;
        }
      }
      return true;
    }

    private static int[] parseVersion(String value) {
      String clean = value == null ? "" : value.trim().replaceFirst("^[vV]", "").split("[-+]", 2)[0];
      String[] parts = clean.split("\\.");
      if (parts.length == 0 || parts[0].isBlank()) throw new IllegalArgumentException("Invalid version: " + value);
      int[] result = {0, 0, 0};
      for (int i = 0; i < Math.min(3, parts.length); i++) result[i] = Integer.parseInt(parts[i]);
      return result;
    }

    private static int compareVersions(int[] left, int[] right) {
      for (int i = 0; i < 3; i++) {
        int comparison = Integer.compare(left[i], right[i]);
        if (comparison != 0) return comparison;
      }
      return 0;
    }
    private static Verification failed(Path p, Descriptor d, String e, long s, Instant m, String h) { return new Verification(p, d, e, s, m, h); }
    private static long size(Path p) { try { return p == null ? -1 : Files.size(p); } catch (IOException e) { return -1; } }
    private static Instant modified(Path p) { try { return p == null ? null : Files.getLastModifiedTime(p).toInstant(); } catch (IOException e) { return null; } }
    private static String hash(Path p) {
      if (p == null || !Files.isRegularFile(p)) return "";
      try (InputStream input = Files.newInputStream(p)) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[32768];
        for (int n; (n = input.read(buffer)) >= 0; ) if (n > 0) digest.update(buffer, 0, n);
        return HexFormat.of().formatHex(digest.digest());
      } catch (Exception e) { return ""; }
    }
  }

  /** Strict dependency-free JSON reader for the standalone Hub. */
  private static final class Json {
    private final String text; private int at;
    Json(String text) { this.text = text == null ? "" : text; }
    Object parse() throws IOException { ws(); Object value = value(); ws(); if (at != text.length()) fail("Trailing content"); return value; }
    private Object value() throws IOException {
      ws(); if (at >= text.length()) return fail("Unexpected end of JSON");
      return switch (text.charAt(at)) { case '{' -> object(); case '[' -> array(); case '"' -> string();
        case 't' -> literal("true", true); case 'f' -> literal("false", false); case 'n' -> literal("null", null); default -> number(); };
    }
    private Map<String, Object> object() throws IOException {
      expect('{'); Map<String, Object> map = new LinkedHashMap<>(); ws(); if (take('}')) return map;
      while (true) { ws(); if (at >= text.length() || text.charAt(at) != '"') fail("Object key must be a string");
        String key = string(); if (map.containsKey(key)) fail("Duplicate object key '" + key + "'"); expect(':'); map.put(key, value()); ws();
        if (take('}')) return map; expect(','); }
    }
    private List<Object> array() throws IOException {
      expect('['); List<Object> list = new ArrayList<>(); ws(); if (take(']')) return list;
      while (true) { list.add(value()); ws(); if (take(']')) return list; expect(','); }
    }
    private String string() throws IOException {
      expect('"'); StringBuilder out = new StringBuilder();
      while (at < text.length()) { char c = text.charAt(at++); if (c == '"') return out.toString();
        if (c == '\\') { if (at >= text.length()) return fail("Incomplete escape"); char e = text.charAt(at++);
          switch (e) { case '"', '\\', '/' -> out.append(e); case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
            case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t'); case 'u' -> out.append(unicode()); default -> fail("Invalid escape"); }
        } else { if (c < 32) fail("Control character in string"); out.append(c); } }
      return fail("Unterminated string");
    }
    private char unicode() throws IOException {
      if (at + 4 > text.length()) return fail("Incomplete unicode escape");
      try { char c = (char) Integer.parseInt(text.substring(at, at + 4), 16); at += 4; return c; }
      catch (NumberFormatException e) { return fail("Invalid unicode escape"); }
    }
    private Object number() throws IOException {
      int start = at; if (take('-') && at >= text.length()) return fail("Incomplete number");
      while (at < text.length() && "0123456789+-.eE".indexOf(text.charAt(at)) >= 0) at++;
      try { return Double.valueOf(text.substring(start, at)); } catch (NumberFormatException e) { return fail("Invalid JSON value"); }
    }
    private Object literal(String word, Object value) throws IOException { if (!text.startsWith(word, at)) return fail("Expected " + word); at += word.length(); return value; }
    private void ws() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }
    private boolean take(char c) { if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
    private void expect(char c) throws IOException { ws(); if (!take(c)) fail("Expected '" + c + "'"); }
    private <T> T fail(String message) throws IOException { throw new IOException(message + " at character " + at); }
  }

  @FunctionalInterface private interface DocumentListenerAdapter extends javax.swing.event.DocumentListener {
    void update(DocumentEvent event);
    @Override default void insertUpdate(DocumentEvent e) { update(e); }
    @Override default void removeUpdate(DocumentEvent e) { update(e); }
    @Override default void changedUpdate(DocumentEvent e) { update(e); }
  }
}
