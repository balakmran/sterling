package com.example.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderRepository;
import com.example.orders.service.OrderService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
    private final OrderService service = new OrderService(new OrderRepository());

    @Test
    void placesAndFindsAnOrder() {
        Order placed = service.place("ada", new BigDecimal("10"));

        assertThat(service.get(placed.getId()).getCustomer()).isEqualTo("ada");
        assertThat(service.all()).hasSize(1);
    }

    @Test
    void rejectsANonPositiveTotal() {
        assertThatThrownBy(() -> service.place("ada", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
