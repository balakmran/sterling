package com.example.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public class CreateStockRequest {
  @NotBlank(message = "sku is required and must not be blank") @Size(max = 64, message = "sku exceeds the maximum allowed length of 64 characters") private final String sku;

  @Nullable private final String note;

  public CreateStockRequest(String sku, @Nullable String note) {
    this.sku = sku;
    this.note = note;
  }

  public String sku() {
    return sku;
  }

  @Nullable public String note() {
    return note;
  }
}
