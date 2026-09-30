package com.example.notification_service.service;

import com.example.notification_service.model.OrderResponse;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    public void sendNotification(OrderResponse order) {
        System.out.println("Order ID: " + order.getOrderId());
        System.out.println("Product: " + order.getProduct());
        System.out.println("Quantity: " + order.getQuantity());
        System.out.println("Status: " + order.getStatus());
    }
}
