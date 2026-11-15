package com.example.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderStatus;
import com.example.orders.web.OrderDto;
import com.example.orders.web.OrderMapperImpl;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderMapperTest {
    @Test
    void mapsAnOrder() {
        Order order = Order.builder().id("1").customer("ada").total(BigDecimal.TEN).status(OrderStatus.PAID).build();

        OrderDto dto = new OrderMapperImpl().toDto(order);

        assertThat(dto.getCustomer()).isEqualTo("ada");
        assertThat(dto.getStatus()).isEqualTo("PAID");
    }
}
