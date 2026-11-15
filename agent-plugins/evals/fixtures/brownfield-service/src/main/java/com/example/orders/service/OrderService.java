package com.example.orders.service;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderRepository;
import com.example.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order place(String customer, BigDecimal total) {
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("total must be positive");
        }
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .customer(customer)
                .total(total)
                .status(OrderStatus.PLACED)
                .build();
        return repository.save(order);
    }

    public Order get(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalStateException("no order " + id));
    }

    public List<Order> all() {
        return repository.findAll();
    }

    public Order cancel(String id) {
        Order order = get(id);
        if (order.getStatus() == OrderStatus.PAID) {
            throw new IllegalStateException("paid orders can't be cancelled");
        }
        return repository.save(order.withStatus(OrderStatus.CANCELLED));
    }

    public BigDecimal revenue() {
        BigDecimal sum = BigDecimal.ZERO;
        for (Order order : repository.findAll()) {
            if (order.getStatus() == OrderStatus.PAID) {
                sum = sum.add(order.getTotal());
            } else if (order.getStatus() == OrderStatus.PLACED) {
                sum = sum.add(order.getTotal().multiply(new BigDecimal("0.5")));
            }
        }
        return sum;
    }
}
