package com.example.order_service.service;

import com.example.order_service.kafka.OrderProducer;
import com.example.order_service.model.OrderRequest;
import com.example.order_service.model.OrderResponse;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderProducer orderProducer;
    private long orderCounter = 0;

    public OrderService(OrderProducer orderProducer) {
        this.orderProducer = orderProducer;
    }

    public OrderResponse placeOrder(OrderRequest order) {
        long orderId = orderCounter++;
        OrderResponse response = new OrderResponse(
                (int) orderId,
                order.getProduct(),
                order.getQuantity(),
                "CREATED"
        );
        orderProducer.sendOrder(response);
        return response;
    }
}
