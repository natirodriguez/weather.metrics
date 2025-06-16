package com.example.weather.metrics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableCaching
@EnableFeignClients
public class Application {
    private static final Logger logger = LogManager.getLogger(Application.class);

	public static void main(String[] args) {
		logger.info("Corriendo Weather Metrics.");
		
		SpringApplication.run(Application.class, args);
	}

}
