package com.example.orders.web;

import com.example.orders.api.OrdersApi;
import com.example.orders.service.OrderService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController implements OrdersApi {
    private final OrderService service;
    private final OrderMapper mapper;

    public OrderController(OrderService service, OrderMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    @GetMapping("/orders/{id}")
    public OrderDto getOrder(@PathVariable String id) {
        return mapper.toDto(service.get(id));
    }

    @Override
    @GetMapping("/orders")
    public List<OrderDto> listOrders() {
        return service.all().stream().map(mapper::toDto).toList();
    }

    @Override
    @PostMapping("/orders")
    public OrderDto placeOrder(@RequestBody OrderDto order) {
        return mapper.toDto(service.place(order.getCustomer(), order.getTotal()));
    }
}
