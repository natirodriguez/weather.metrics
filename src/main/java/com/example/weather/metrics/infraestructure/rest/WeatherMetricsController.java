package com.example.weather.metrics.infraestructure.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.weather.metrics.service.WeatherMetricsService;

import java.util.Map;

@RestController
public class WeatherMetricsController {

    private final WeatherMetricsService weatherMetricsService;

    @Autowired
    public WeatherMetricsController(WeatherMetricsService weatherMetricsService) {
        this.weatherMetricsService = weatherMetricsService;
    }

    @GetMapping("/weather/current")
    public Map<String, Double> getCurrentTemperatures() {
        return weatherMetricsService.getCurrentTemperatures();
    }

    @GetMapping("/weather/average/day")
    public Map<String, Double> getAverageTemperatureLastDay() {
        return weatherMetricsService.getAverageTemperatureLastDay();
    }

    @GetMapping("/weather/average/week")
    public Map<String, Double> getAverageTemperatureLastWeek() {
        return weatherMetricsService.getAverageTemperatureLastWeek();
    }
}
