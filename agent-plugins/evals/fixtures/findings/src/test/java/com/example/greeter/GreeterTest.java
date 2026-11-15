package com.example.greeter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GreeterTest {
  @Test
  void greetsTheWorld() {
    assertThat(new Greeter("Hello").greet(null)).isEqualTo("Hello, world");
  }
}
