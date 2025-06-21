package com.example.weather.metrics.infraestructure.rest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.weather.metrics.service.WeatherMetricsService;
import java.util.Map;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

@RestController
public class WeatherMetricsController {
    private static final Logger logger = LogManager.getLogger(WeatherMetricsController.class);
    private final Counter currentTemperatureCounter;
    private final Counter averageTemperatureLastDayCounter;
    private final Counter averageTemperatureLastWeekCounter;
    private final WeatherMetricsService weatherMetricsService;

    public WeatherMetricsController(WeatherMetricsService weatherMetricsService, MeterRegistry meterRegistry) {
        this.weatherMetricsService = weatherMetricsService;
        this.currentTemperatureCounter = meterRegistry.counter("weather.current.temperature.requests");
        this.averageTemperatureLastDayCounter = meterRegistry.counter("weather.average.temperature.lastday.requests");
        this.averageTemperatureLastWeekCounter = meterRegistry.counter("weather.average.temperature.lastweek.requests");
    }

    @GetMapping("/weather/current")
    public Map<String, Double> getCurrentTemperatures() {
    	logger.info("Ejecución del endpoint: getCurrentTemperatures()");
        currentTemperatureCounter.increment(); 
        
        return weatherMetricsService.getCurrentTemperatures();
    }
    

    @GetMapping("/weather/average/today")
    public Map<String, Double> getAverageTemperatureLastDay() {
    	logger.info("Ejecución del endpoint: getAverageTemperatureLastDay()");
        averageTemperatureLastDayCounter.increment();

        return weatherMetricsService.getAverageTemperatureLastDay();
    }

    @GetMapping("/weather/average/week")
    public Map<String, Double> getAverageTemperatureLastWeek() {
    	logger.info("Ejecución del endpoint: getAverageTemperatureLastWeek()");
    	averageTemperatureLastWeekCounter.increment();
    	
        return weatherMetricsService.getAverageTemperatureLastWeek();
    }
}
