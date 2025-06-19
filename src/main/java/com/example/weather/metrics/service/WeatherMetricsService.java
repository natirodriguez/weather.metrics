package com.example.weather.metrics.service;

import com.example.weather.metrics.infraestructure.client.LoaderClient;
import com.example.weather.metrics.infraestructure.client.WeatherDataDTO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class WeatherMetricsService {
    private static final Logger logger = LogManager.getLogger(WeatherMetricsService.class);

    private final LoaderClient loaderClient; 
    private final Counter currentTemperatureCounter;
    private final Counter averageTemperatureLastDayCounter;
    private final Counter averageTemperatureLastWeekCounter;
    
    public WeatherMetricsService(LoaderClient loaderClient, MeterRegistry meterRegistry) {
		this.loaderClient = loaderClient;
        this.currentTemperatureCounter = meterRegistry.counter("weather.current.temperature.service.calls");
        this.averageTemperatureLastDayCounter = meterRegistry.counter("weather.average.temperature.lastday.service.calls");
        this.averageTemperatureLastWeekCounter = meterRegistry.counter("weather.average.temperature.lastweek.service.calls");
    }
    
    @Cacheable(value = "currentTemperature", key = "'current'")
    public Map<String, Double> getCurrentTemperatures() {
    	currentTemperatureCounter.increment();
    	
        WeatherDataDTO currentData = loaderClient.obtenerTemperaturaActual();
        Map<String, Double> currentTemps = new TreeMap<>();
        if (currentData != null) {
            currentTemps.put(currentData.getCity(), currentData.getTemperature());
        }
        else {
        	logger.error("Current data es nulo");
        }
        return currentTemps;
    }
    
    public Map<String, Double> getAverageTemperatureLastDay() {
    	averageTemperatureLastDayCounter.increment();
    	
        List<WeatherDataDTO> dailyData = loaderClient.obtenerTemperaturasHoy();
        return calculateAverage(dailyData);
    }

    public Map<String, Double> getAverageTemperatureLastWeek() {
    	averageTemperatureLastDayCounter.increment();
    	
        List<WeatherDataDTO> weeklyData = loaderClient.obtenerTemperaturasUltimaSemana();
        return calculateAverage(weeklyData);
    }

    private Map<String, Double> calculateAverage(List<WeatherDataDTO> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return new TreeMap<>();
        }

        // Agrupa los datos por ciudad y calcula el promedio para cada ciudad
        return dataList.stream()
                .collect(Collectors.groupingBy(
                        WeatherDataDTO::getCity,
                        Collectors.averagingDouble(WeatherDataDTO::getTemperature)
                ));
    }
}
