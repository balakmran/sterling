package com.example.orders.domain;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.jspecify.annotations.Nullable;

@Value
@Builder
@With
public class Order {
    String id;
    String customer;
    BigDecimal total;
    OrderStatus status;
    @Nullable String note;
}
