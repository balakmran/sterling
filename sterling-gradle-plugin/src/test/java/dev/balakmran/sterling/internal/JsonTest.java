package dev.balakmran.sterling.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
  @Test
  void writesScalarsCompactly() {
    assertThat(Json.write(null)).isEqualTo("null");
    assertThat(Json.write(true)).isEqualTo("true");
    assertThat(Json.write(25)).isEqualTo("25");
    assertThat(Json.write("STANDARD")).isEqualTo("\"STANDARD\"");
  }

  @Test
  void keepsMapOrderAndNestsCollections() {
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("z", 1);
    inner.put("a", Arrays.asList("x", null));
    Map<String, Object> outer = new LinkedHashMap<>();
    outer.put("b", inner);
    outer.put("empty", List.of());

    assertThat(Json.write(outer)).isEqualTo("{\"b\":{\"z\":1,\"a\":[\"x\",null]},\"empty\":[]}");
  }

  @Test
  void escapesQuotesBackslashesAndControlCharacters() {
    assertThat(Json.write("a\"b\\c\nd\re\tf\bg\fh\u0001i/é"))
        .isEqualTo("\"a\\\"b\\\\c\\nd\\re\\tf\\bg\\fh\\u0001i/é\"");
  }

  @Test
  void rejectsOtherTypes() {
    assertThatThrownBy(() -> Json.write(1.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("java.lang.Double");
  }
}
