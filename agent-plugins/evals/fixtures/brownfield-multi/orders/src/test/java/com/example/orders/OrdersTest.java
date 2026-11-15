package com.example.orders;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrdersTest {
    @Test
    void countsLines() {
        Orders orders = new Orders();
        orders.add(" tea ");

        assertThat(orders.count()).isEqualTo(1);
        assertThat(orders.summary()).isEqualTo("one order: tea");
    }
}
