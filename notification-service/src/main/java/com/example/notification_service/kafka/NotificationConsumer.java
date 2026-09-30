package com.example.notification_service.kafka;

import ch.qos.logback.core.testUtil.NPEAppender;
import com.example.notification_service.model.OrderResponse;
import com.example.notification_service.service.NotificationService;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Service
public class NotificationConsumer {
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationConsumer(ObjectMapper objectMapper,
                                NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    // This will create dlt topic in kafka by itself
    @DltHandler
    public void dltHandle(String message) {
        System.out.println("DLT message is: " + message);
    }

    @RetryableTopic(attempts = "5")
    @KafkaListener(
            topics = "order-created",
            groupId = "notification-group"
    )
    public void consume(String message) {
        OrderResponse order = objectMapper
                .readValue(message, OrderResponse.class);
        /*
        TRIAL FOR DEAD LETTER TOPIC(DLT)

        if (Objects.equals(order.getProduct(), "FAIL")) {
            throw new RuntimeException("order cant be proceed");
        }

         */
        notificationService.sendNotification(order);
    }
}
