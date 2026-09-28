package com.example.order_service.controller;

import com.example.order_service.OrderServiceApplication;
import com.example.order_service.model.OrderRequest;
import com.example.order_service.model.OrderResponse;
import com.example.order_service.service.OrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse placeOrder(@RequestBody OrderRequest order) {
        return orderService.placeOrder(order);
    }
}
