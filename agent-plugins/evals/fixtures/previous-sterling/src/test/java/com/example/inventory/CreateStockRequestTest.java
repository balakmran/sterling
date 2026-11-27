package com.example.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreateStockRequestTest {
  @Test
  void keepsWhatItWasGiven() {
    CreateStockRequest request = new CreateStockRequest("tea", null);

    assertThat(request.sku()).isEqualTo("tea");
    assertThat(request.note()).isNull();
    assertThat(new CreateStockRequest("tea", "loose leaf").note()).isEqualTo("loose leaf");
  }
}
