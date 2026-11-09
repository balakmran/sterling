package dev.balakmran.sterling;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * The tool versions Sterling pins, from {@code versions.properties} in the plugin jar.
 *
 * <p>Sterling's build generates that resource from {@code gradle/libs.versions.toml}, so the
 * versions configured for consumers and the ones in the catalog can't drift apart.
 */
final class ToolVersions {
  static final String RESOURCE = "versions.properties";

  private ToolVersions() {}

  /**
   * All pinned versions.
   *
   * @return the versions keyed by tool name, plus {@code sterling} for Sterling's own version
   */
  static Map<String, String> all() {
    return Holder.ALL;
  }

  /**
   * One pinned version.
   *
   * @param key the tool name, such as {@code google-java-format}
   * @return the version
   * @throws IllegalStateException if the resource has no such entry, which means a broken build
   */
  static String get(String key) {
    String version = all().get(key);
    if (version == null) {
      throw new IllegalStateException("Sterling's " + RESOURCE + " has no '" + key + "' entry.");
    }
    return version;
  }

  // Loaded once, on first use.
  private static final class Holder {
    static final Map<String, String> ALL = load();
  }

  private static Map<String, String> load() {
    Properties props = new Properties();
    try (InputStream in = ToolVersions.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Sterling's " + RESOURCE + " is missing from the jar.");
      }
      props.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Map<String, String> versions = new TreeMap<>();
    props.stringPropertyNames().forEach(key -> versions.put(key, props.getProperty(key)));
    return Map.copyOf(versions);
  }
}
