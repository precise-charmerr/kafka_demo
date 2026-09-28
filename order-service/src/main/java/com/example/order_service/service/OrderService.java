package com.example.order_service.service;

import com.example.order_service.kafka.OrderProducer;
import com.example.order_service.model.OrderRequest;
import com.example.order_service.model.OrderResponse;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderProducer orderProducer;

    public OrderService(OrderProducer orderProducer) {
        this.orderProducer = orderProducer;
    }

    public OrderResponse placeOrder(OrderRequest order) {
        OrderResponse response = new OrderResponse(
                1,
                order.getProduct(),
                order.getQuantity(),
                "CREATED"
        );
        orderProducer.sendOrder(response);
        return response;
    }
}
