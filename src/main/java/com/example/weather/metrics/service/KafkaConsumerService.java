package com.example.weather.metrics.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collections;
import java.util.function.Consumer;

@Service
public class KafkaConsumerService {

    private final KafkaConsumer<String, String> kafkaConsumer;
    private volatile boolean running = true;
    private Thread consumerThread;
    private Consumer<String> messageProcessor;

    public KafkaConsumerService(KafkaConsumer<String, String> kafkaConsumer) {
        this.kafkaConsumer = kafkaConsumer;
    }

    public void startConsuming(Consumer<String> processor) {
        this.messageProcessor = processor;
        kafkaConsumer.subscribe(Collections.singletonList("weather-data"));
        running = true;

        consumerThread = new Thread(() -> {
            try {
                while (running) {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofSeconds(1));
                    for (ConsumerRecord<String, String> record : records) {
                        if (messageProcessor != null) {
                            messageProcessor.accept(record.value());
                        }
                    }
                }
            } catch (WakeupException e) {
                // Expected on shutdown
                if (running) {
                    e.printStackTrace();
                }
            } finally {
                kafkaConsumer.close();
            }
        });
        consumerThread.start();
    }

    @PreDestroy
    public void stopConsuming() {
        running = false;
        kafkaConsumer.wakeup();
        try {
            if (consumerThread != null) {
                consumerThread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
