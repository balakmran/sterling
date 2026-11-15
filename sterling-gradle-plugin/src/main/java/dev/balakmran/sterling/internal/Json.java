package dev.balakmran.sterling.internal;

import java.util.Collection;
import java.util.Map;

/**
 * Writes JSON on one line, for {@code stlInfo --json}.
 *
 * <p>It handles only what Sterling's report holds: {@code null}, strings, booleans, numbers, maps
 * with string keys (written in their iteration order) and collections. Sterling writes its own
 * rather than adding a JSON library, because every dependency of Sterling lands on its consumers'
 * build classpath.
 */
final class Json {
  private Json() {}

  /**
   * Writes a value as compact JSON.
   *
   * @param value the value to write
   * @return the JSON text, without whitespace between tokens
   * @throws IllegalArgumentException if the value, or anything inside it, has another type
   */
  static String write(Object value) {
    StringBuilder out = new StringBuilder();
    append(out, value);
    return out.toString();
  }

  private static void append(StringBuilder out, Object value) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof String string) {
      appendString(out, string);
    } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
      out.append(value);
    } else if (value instanceof Map<?, ?> map) {
      out.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) {
          out.append(',');
        }
        first = false;
        appendString(out, (String) entry.getKey());
        out.append(':');
        append(out, entry.getValue());
      }
      out.append('}');
    } else if (value instanceof Collection<?> collection) {
      out.append('[');
      boolean first = true;
      for (Object element : collection) {
        if (!first) {
          out.append(',');
        }
        first = false;
        append(out, element);
      }
      out.append(']');
    } else {
      throw new IllegalArgumentException("Can't write " + value.getClass().getName() + " as JSON");
    }
  }

  // RFC 8259: quote, backslash and control characters must be escaped.
  private static void appendString(StringBuilder out, String value) {
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }
}
