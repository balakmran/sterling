package com.example.inventory;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class Stock {
  private final Map<String, Integer> counts = new HashMap<>();

  public void add(String sku, int amount) {
    counts.merge(sku, amount, Integer::sum);
  }

  public int count(String sku) {
    return counts.getOrDefault(sku, 0);
  }

  public boolean has(String sku) {
    return count(sku) > 0;
  }
}
