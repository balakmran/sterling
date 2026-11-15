package com.example.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StockTest {
  @Test
  void countsWhatWasAdded() {
    Stock stock = new Stock();
    stock.add("tea", 2);
    stock.add("tea", 3);

    assertThat(stock.count("tea")).isEqualTo(5);
    assertThat(stock.has("tea")).isTrue();
    assertThat(stock.has("milk")).isFalse();
  }
}
