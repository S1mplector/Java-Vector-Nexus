package com.jvn.plugin.runtime;

import com.jvn.plugin.api.BundledPluginProvider;
import com.jvn.plugin.api.JvnPlugin;
import com.jvn.plugin.api.PluginContext;
import com.jvn.plugin.api.PluginDependency;
import com.jvn.plugin.api.PluginDescriptor;
import com.jvn.plugin.api.PluginEnvironment;
import com.jvn.plugin.api.PluginRegistries;
import com.jvn.plugin.api.runtime.RuntimeEvent;
import com.jvn.plugin.api.runtime.RuntimeListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Discovers, validates, starts, and stops plugins for one JVN process. */
public final class PluginHost implements AutoCloseable {
  public static final String API_VERSION = "1.2.0";
  private static final Logger log = LoggerFactory.getLogger(PluginHost.class);

  private final String jvnVersion;
  private final PluginEnvironment environment;
  private final Path projectDirectory;
  private final Path userDataDirectory;
  private final List<Path> pluginDirectories;
  private final DefaultPluginRegistries registries = new DefaultPluginRegistries();
  private final PluginBundleVerifier bundleVerifier = new PluginBundleVerifier();
  private final Map<String, Candidate> candidates = new LinkedHashMap<>();
  private final Map<String, Active> active = new LinkedHashMap<>();
  private final List<PluginDiagnostic> diagnostics = new ArrayList<>();
  private boolean started;
  private boolean closed;

  private PluginHost(Builder builder) {
    jvnVersion = builder.jvnVersion;
    environment = builder.environment;
    projectDirectory = builder.projectDirectory;
    userDataDirectory = builder.userDataDirectory;
    pluginDirectories = List.copyOf(builder.pluginDirectories);
  }

  public static Builder builder(PluginEnvironment environment) { return new Builder(environment); }

  public synchronized void discover() {
    ensureNotStarted();
    if (Boolean.getBoolean("jvn.plugins.disabled")) {
      diagnostic(PluginDiagnostic.Severity.INFO, "", "disabled", "Plugin loading is disabled", null);
      return;
    }
    for (Path directory : pluginDirectories) discoverDirectory(directory);
    for (BundledPluginProvider provider : ServiceLoader.load(BundledPluginProvider.class)) {
      try {
        addCandidate(new Candidate(provider.descriptor(), provider.create(), null, null));
      } catch (Throwable error) {
        diagnostic(PluginDiagnostic.Severity.ERROR, "", "bundled-provider", "Bundled plugin provider failed", error);
      }
    }
  }

  /** Adds a plugin supplied directly by an embedding application or test harness. */
  public synchronized void addBundled(BundledPluginProvider provider) {
    ensureNotStarted();
    if (provider == null) throw new IllegalArgumentException("Plugin provider is required");
    addCandidate(new Candidate(provider.descriptor(), provider.create(), null, null));
  }

  public synchronized void start() {
    ensureNotStarted();
    started = true;
    List<Candidate> ordered = dependencyOrder();
    for (Candidate candidate : ordered) initialize(candidate);
    for (Candidate candidate : ordered) {
      Active loaded = active.get(candidate.descriptor.id());
      if (loaded == null || loaded.state != PluginState.INITIALIZED) continue;
      boolean dependencyFailed = candidate.descriptor.dependencies().stream().anyMatch(dependency -> {
        Active required = active.get(dependency.id());
        return required == null || required.state != PluginState.STARTED;
      });
      if (dependencyFailed) {
        fail(loaded, "dependency-start-failed", "A required plugin did not start", null);
        continue;
      }
      try {
        loaded.plugin.start();
        loaded.state = PluginState.STARTED;
      } catch (Throwable error) {
        fail(loaded, "start-failed", "Plugin start failed", error);
      }
    }
    RuntimeEvent event = new RuntimeEvent(jvnVersion, projectDirectory, Map.of("environment", environment.name()));
    dispatch(listener -> listener.onRuntimeStarted(event), "runtime-start-listener");
    if (projectDirectory != null) dispatch(listener -> listener.onProjectOpened(event), "project-open-listener");
  }

  public synchronized void discoverAndStart() {
    discover();
    start();
  }

  public PluginRegistries registries() { return registries.view(); }
  public synchronized List<PluginDiagnostic> diagnostics() { return List.copyOf(diagnostics); }

  public synchronized List<LoadedPlugin> plugins() {
    List<LoadedPlugin> result = new ArrayList<>();
    for (Candidate candidate : candidates.values()) {
      Active loaded = active.get(candidate.descriptor.id());
      result.add(new LoadedPlugin(candidate.descriptor, loaded == null ? PluginState.DISCOVERED : loaded.state,
          candidate.source, loaded == null ? "" : loaded.failure));
    }
    return List.copyOf(result);
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    RuntimeEvent event = new RuntimeEvent(jvnVersion, projectDirectory, Map.of("environment", environment.name()));
    dispatch(listener -> listener.onRuntimeStopping(event), "runtime-stop-listener");
    List<Active> reverse = new ArrayList<>(active.values());
    java.util.Collections.reverse(reverse);
    for (Active loaded : reverse) {
      try {
        if (loaded.state != PluginState.FAILED) loaded.plugin.stop();
      } catch (Throwable error) {
        diagnostic(PluginDiagnostic.Severity.WARNING, loaded.candidate.descriptor.id(), "stop-failed", "Plugin stop failed", error);
      } finally {
        registries.removePlugin(loaded.candidate.descriptor.id());
        loaded.state = PluginState.STOPPED;
        closeLoader(loaded.candidate.loader);
      }
    }
    candidates.values().forEach(candidate -> closeLoader(candidate.loader));
    started = false;
  }

  private void discoverDirectory(Path directory) {
    if (directory == null || !Files.isDirectory(directory)) return;
    try (var files = Files.list(directory)) {
      files.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".jar"))
          .sorted().forEach(this::discoverJar);
    } catch (IOException error) {
      diagnostic(PluginDiagnostic.Severity.WARNING, "", "directory-read", "Could not scan plugin directory " + directory, error);
    }
  }

  private void discoverJar(Path jar) {
    URLClassLoader loader = null;
    try {
      PluginBundleVerifier.Verification verification = bundleVerifier.verify(jar);
      if (!verification.isValid()) {
        diagnostic(PluginDiagnostic.Severity.ERROR, "", "jar-verify",
            "Could not verify plugin jar " + jar + ": " + verification.error(), null);
        return;
      }
      loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, JvnPlugin.class.getClassLoader());
      PluginDescriptor descriptor = verification.descriptor();
      Class<?> type = Class.forName(descriptor.entrypoint(), true, loader);
      JvnPlugin plugin = (JvnPlugin) type.getDeclaredConstructor().newInstance();
      addCandidate(new Candidate(descriptor, plugin, jar.toAbsolutePath(), loader));
      loader = null;
    } catch (Throwable error) {
      diagnostic(PluginDiagnostic.Severity.ERROR, "", "jar-load", "Could not load plugin jar " + jar, error);
    } finally {
      closeLoader(loader);
    }
  }

  private void addCandidate(Candidate candidate) {
    String id = candidate.descriptor.id();
    if (candidates.containsKey(id)) {
      diagnostic(PluginDiagnostic.Severity.ERROR, id, "duplicate-id", "Duplicate plugin id; ignoring " + candidate.source, null);
      closeLoader(candidate.loader);
      return;
    }
    if (!VersionRange.accepts(candidate.descriptor.apiVersion(), API_VERSION)) {
      diagnostic(PluginDiagnostic.Severity.ERROR, id, "api-incompatible",
          "Plugin requires JVN API " + candidate.descriptor.apiVersion() + ", host provides " + API_VERSION, null);
      closeLoader(candidate.loader);
      return;
    }
    candidates.put(id, candidate);
  }

  private List<Candidate> dependencyOrder() {
    List<Candidate> ordered = new ArrayList<>();
    Set<String> visiting = new LinkedHashSet<>();
    Set<String> visited = new LinkedHashSet<>();
    for (Candidate candidate : candidates.values()) visit(candidate, visiting, visited, ordered);
    return ordered;
  }

  private boolean visit(Candidate candidate, Set<String> visiting, Set<String> visited, List<Candidate> ordered) {
    String id = candidate.descriptor.id();
    if (visited.contains(id)) return true;
    if (!visiting.add(id)) {
      diagnostic(PluginDiagnostic.Severity.ERROR, id, "dependency-cycle", "Plugin dependency cycle includes " + id, null);
      return false;
    }
    boolean valid = true;
    for (PluginDependency dependency : candidate.descriptor.dependencies()) {
      Candidate required = candidates.get(dependency.id());
      if (required == null) {
        diagnostic(PluginDiagnostic.Severity.ERROR, id, "dependency-missing", "Missing dependency " + dependency.id(), null);
        valid = false;
      } else if (!VersionRange.accepts(dependency.version(), required.descriptor.version())) {
        diagnostic(PluginDiagnostic.Severity.ERROR, id, "dependency-incompatible",
            "Dependency " + dependency.id() + " requires " + dependency.version() + " but found " + required.descriptor.version(), null);
        valid = false;
      } else if (!visit(required, visiting, visited, ordered)) valid = false;
    }
    visiting.remove(id);
    visited.add(id);
    if (valid) ordered.add(candidate);
    else closeLoader(candidate.loader);
    return valid;
  }

  private void initialize(Candidate candidate) {
    PluginDescriptor descriptor = candidate.descriptor;
    try {
      boolean dependencyFailed = descriptor.dependencies().stream().anyMatch(dependency -> {
        Active required = active.get(dependency.id());
        return required == null || required.state != PluginState.INITIALIZED;
      });
      if (dependencyFailed) {
        diagnostic(PluginDiagnostic.Severity.ERROR, descriptor.id(), "dependency-initialize-failed",
            "A required plugin did not initialize", null);
        closeLoader(candidate.loader);
        return;
      }
      Path dataDirectory = userDataDirectory.resolve(descriptor.id());
      Files.createDirectories(dataDirectory);
      Map<String, String> configuration = loadConfiguration(dataDirectory.resolve("config.properties"));
      Logger pluginLogger = LoggerFactory.getLogger("jvn.plugin." + descriptor.id());
      PluginContext context = new DefaultPluginContext(descriptor, environment, jvnVersion, dataDirectory,
          projectDirectory, configuration, pluginLogger, registries.forPlugin(descriptor),
          registries.contributionsFor(descriptor));
      Active loaded = new Active(candidate);
      active.put(descriptor.id(), loaded);
      candidate.plugin.initialize(context);
      loaded.state = PluginState.INITIALIZED;
      diagnostic(PluginDiagnostic.Severity.INFO, descriptor.id(), "initialized", "Plugin initialized", null);
    } catch (Throwable error) {
      Active loaded = active.computeIfAbsent(descriptor.id(), ignored -> new Active(candidate));
      fail(loaded, "initialize-failed", "Plugin initialization failed", error);
    }
  }

  private void fail(Active loaded, String code, String message, Throwable error) {
    loaded.state = PluginState.FAILED;
    loaded.failure = error == null || error.getMessage() == null ? message : error.getMessage();
    registries.removePlugin(loaded.candidate.descriptor.id());
    try { loaded.plugin.stop(); }
    catch (Throwable cleanupError) {
      diagnostic(PluginDiagnostic.Severity.WARNING, loaded.candidate.descriptor.id(), "rollback-failed",
          "Plugin rollback failed", cleanupError);
    }
    diagnostic(PluginDiagnostic.Severity.ERROR, loaded.candidate.descriptor.id(), code, message, error);
  }

  private void dispatch(ListenerCall call, String code) {
    for (var entry : registries.view().runtimeListeners().entries()) {
      try { call.invoke(entry.extension()); }
      catch (Throwable error) { diagnostic(PluginDiagnostic.Severity.WARNING, entry.pluginId(), code, "Plugin listener failed", error); }
    }
  }

  private void diagnostic(PluginDiagnostic.Severity severity, String pluginId, String code, String message, Throwable error) {
    diagnostics.add(new PluginDiagnostic(severity, pluginId, code, message, error));
    if (severity == PluginDiagnostic.Severity.ERROR) log.error("Plugin {} [{}]: {}", pluginId, code, message, error);
    else if (severity == PluginDiagnostic.Severity.WARNING) log.warn("Plugin {} [{}]: {}", pluginId, code, message, error);
    else log.info("Plugin {}: {}", pluginId, message);
  }

  private static Map<String, String> loadConfiguration(Path path) throws IOException {
    if (!Files.isRegularFile(path)) return Map.of();
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
    Map<String, String> result = new LinkedHashMap<>();
    for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key));
    return Map.copyOf(result);
  }

  private void ensureNotStarted() {
    if (closed) throw new IllegalStateException("Plugin host is closed; create a new host to reload");
    if (started) throw new IllegalStateException("Plugin host is already started");
  }

  private static void closeLoader(URLClassLoader loader) {
    if (loader == null) return;
    try { loader.close(); } catch (IOException ignored) {}
  }

  private record Candidate(PluginDescriptor descriptor, JvnPlugin plugin, Path source, URLClassLoader loader) {}
  private static final class Active {
    final Candidate candidate;
    final JvnPlugin plugin;
    PluginState state = PluginState.DISCOVERED;
    String failure = "";
    Active(Candidate candidate) { this.candidate = candidate; this.plugin = candidate.plugin; }
  }
  @FunctionalInterface private interface ListenerCall { void invoke(RuntimeListener listener) throws Exception; }

  public static final class Builder {
    private final PluginEnvironment environment;
    private String jvnVersion = System.getProperty("jvn.version", "dev");
    private Path projectDirectory;
    private Path userDataDirectory = Path.of(System.getProperty("user.home"), ".jvn", "plugin-data");
    private final List<Path> pluginDirectories = new ArrayList<>();

    private Builder(PluginEnvironment environment) {
      if (environment == null) throw new IllegalArgumentException("Plugin environment is required");
      this.environment = environment;
      pluginDirectories.add(Path.of(System.getProperty("user.home"), ".jvn", "plugins"));
    }
    public Builder jvnVersion(String value) { if (value != null && !value.isBlank()) jvnVersion = value; return this; }
    public Builder projectDirectory(Path value) {
      projectDirectory = value == null ? null : value.toAbsolutePath().normalize();
      if (projectDirectory != null) pluginDirectories.add(projectDirectory.resolve("plugins"));
      return this;
    }
    public Builder userDataDirectory(Path value) { if (value != null) userDataDirectory = value; return this; }
    public Builder pluginDirectory(Path value) { if (value != null) pluginDirectories.add(value); return this; }
    public PluginHost build() { return new PluginHost(this); }
  }
}
