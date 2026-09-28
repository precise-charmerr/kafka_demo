package com.example.order_service.kafka;

import com.example.order_service.model.OrderResponse;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendOrder(OrderResponse response) {
        String json = objectMapper.writeValueAsString(response);
        kafkaTemplate
                .send("order-created", json)
                .whenComplete((result, exception) -> {
                    if(exception == null) {
                        System.out.println("message sent to kafka");
                    } else {
                        System.out.println("Failed to send message to Kafka");
                        exception.printStackTrace();
                    }
                });
    }
}
