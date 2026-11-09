package dev.balakmran.sterling;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Versions pinned in the version catalog, read from a resource generated at build time. */
final class ToolVersions {
  static final String RESOURCE = "versions.properties";

  private ToolVersions() {}

  static Map<String, String> all() {
    return Holder.ALL;
  }

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
