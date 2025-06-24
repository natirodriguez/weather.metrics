package com.example.weather.metrics.service;

import com.example.weather.metrics.infraestructure.client.LoaderClient;
import com.example.weather.metrics.infraestructure.client.WeatherDataDTO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class WeatherMetricsService {
    private static final Logger logger = LogManager.getLogger(WeatherMetricsService.class);

    private final LoaderClient loaderClient; 
    private final MeterRegistry meterRegistry;
    private static final String DEFAULT_CITY = "Quilmes,ar";

    private final Counter currentTemperatureCounter;
    private final Counter averageTemperatureLastDayCounter;
    private final Counter averageTemperatureLastWeekCounter;
    
    private final Map<String, DoubleHolder> currentTemperatureHolders = new ConcurrentHashMap<>();
    private final Map<String, DoubleHolder> averageTemperatureLastDayHolders = new ConcurrentHashMap<>();
    private final Map<String, DoubleHolder> averageTemperatureLastWeekHolders = new ConcurrentHashMap<>();

    private final Map<String, DoubleHolder> dailyMaxTemperatureHolders = new ConcurrentHashMap<>();
    private final Map<String, DoubleHolder> dailyMinTemperatureHolders = new ConcurrentHashMap<>();
    private final Map<String, DoubleHolder> weeklyMaxTemperatureHolders = new ConcurrentHashMap<>();
    private final Map<String, DoubleHolder> weeklyMinTemperatureHolders = new ConcurrentHashMap<>();

    
    public WeatherMetricsService(LoaderClient loaderClient, MeterRegistry meterRegistry) {
		this.loaderClient = loaderClient;
        this.meterRegistry = meterRegistry;

        this.currentTemperatureCounter = meterRegistry.counter("weather.current.temperature.service.calls");
        this.averageTemperatureLastDayCounter = meterRegistry.counter("weather.average.temperature.lastday.service.calls");
        this.averageTemperatureLastWeekCounter = meterRegistry.counter("weather.average.temperature.lastweek.service.calls");
        
        initializeGauge(currentTemperatureHolders, "weather.current.temperature", DEFAULT_CITY);
        initializeGauge(averageTemperatureLastDayHolders, "weather.average.temperature.lastday", DEFAULT_CITY);
        initializeGauge(averageTemperatureLastWeekHolders, "weather.average.temperature.lastweek", DEFAULT_CITY);

        // --- Inicialización de los nuevos Gauges de Min/Max ---
        initializeGauge(dailyMaxTemperatureHolders, "weather.daily_temperature_max", DEFAULT_CITY);
        initializeGauge(dailyMinTemperatureHolders, "weather.daily_temperature_min", DEFAULT_CITY);
        initializeGauge(weeklyMaxTemperatureHolders, "weather.weekly_temperature_max", DEFAULT_CITY);
        initializeGauge(weeklyMinTemperatureHolders, "weather.weekly_temperature_min", DEFAULT_CITY);

    }
    
    private void initializeGauge(Map<String, DoubleHolder> holdersMap, String metricName, String city) {
        holdersMap.computeIfAbsent(city, k -> {
            DoubleHolder newHolder = new DoubleHolder(Double.NaN); // Usamos NaN para indicar "sin datos" inicial
            // También se podría usar 0.0 si NaN causa problemas en tu visualización, pero NaN es más preciso para "no hay lectura aún"
            Gauge.builder(metricName, newHolder, DoubleHolder::getValue)
                    .tag("city", city)
                    .description("Temperature metric for " + city + ": " + metricName)
                    .register(meterRegistry);
            return newHolder;
        });
    }

    @Scheduled(fixedRate = 3600000) // Cada hora
    public void fetchAndReportCurrentTemperatures() {
        logger.info("Fetching and reporting current temperatures (scheduled).");
        getCurrentTemperatures();
    }

    @Scheduled(fixedRate = 3600000) // Cada hora
    public void fetchAndReportAverageAndMinMaxLastDay() { // Método renombrado para incluir Min/Max
        logger.info("Fetching and reporting average, min, max temperatures last day (scheduled).");
        getAverageTemperatureLastDay(); // Este método ahora se encargará de los 3 valores
    }

    @Scheduled(fixedRate = 3600000) // Cada hora
    public void fetchAndReportAverageAndMinMaxLastWeek() { // Método renombrado para incluir Min/Max
        logger.info("Fetching and reporting average, min, max temperatures last week (scheduled).");
        getAverageTemperatureLastWeek(); // Este método ahora se encargará de los 3 valores
    }
    
    @Cacheable(value = "currentTemperature", key = "'current'")
    public Map<String, Double> getCurrentTemperatures() {
    	currentTemperatureCounter.increment();
        try {
        	
        	WeatherDataDTO currentData = loaderClient.obtenerTemperaturaActual();
        	Map<String, Double> currentTemps = new TreeMap<>();
        	if (currentData != null) {
        		currentTemps.put(currentData.getCity(), currentData.getTemperature());
                updateGaugeValue(currentTemperatureHolders, currentData.getCity(), currentData.getTemperature());
        	}
        	else {
        		logger.error("Current data es nulo");
        	}
        	return currentTemps;
        	
        }catch(Exception e) {
    		logger.error("Error al llamar al servicio satelite Loader", e);
    		throw e;
        }
    }
    
    public Map<String, Double> getAverageTemperatureLastDay() {
    	averageTemperatureLastDayCounter.increment();
    	
        List<WeatherDataDTO> dailyData = loaderClient.obtenerTemperaturasHoy();
        // Calcular y reportar el promedio
        Map<String, Double> averageTemps = calculateAverage(dailyData);
        averageTemps.forEach((city, temp) ->
            updateGaugeValue(averageTemperatureLastDayHolders, city, temp)
        );

        if (!dailyData.isEmpty()) {
            Optional<Double> maxTemp = dailyData.stream()
                                                .map(WeatherDataDTO::getTemperature)
                                                .max(Comparator.naturalOrder());
            maxTemp.ifPresent(val -> updateGaugeValue(dailyMaxTemperatureHolders, DEFAULT_CITY, val));

            Optional<Double> minTemp = dailyData.stream()
                                                .map(WeatherDataDTO::getTemperature)
                                                .min(Comparator.naturalOrder());
            minTemp.ifPresent(val -> updateGaugeValue(dailyMinTemperatureHolders, DEFAULT_CITY, val));
        } else {
            logger.warn("No daily data available to calculate min/max for {}. Setting to NaN.", DEFAULT_CITY);
            updateGaugeValue(dailyMaxTemperatureHolders, DEFAULT_CITY, Double.NaN);
            updateGaugeValue(dailyMinTemperatureHolders, DEFAULT_CITY, Double.NaN);
        }

        return averageTemps;    
    }

    public Map<String, Double> getAverageTemperatureLastWeek() {
    	averageTemperatureLastWeekCounter.increment();
    	
        List<WeatherDataDTO> weeklyData = loaderClient.obtenerTemperaturasUltimaSemana();
        // Calcular y reportar el promedio
        Map<String, Double> averageTemps = calculateAverage(weeklyData);
        averageTemps.forEach((city, temp) ->
            updateGaugeValue(averageTemperatureLastWeekHolders, city, temp)
        );

        // --- Calcular y reportar MIN y MAX para la semana ---
        if (!weeklyData.isEmpty()) {
            Optional<Double> maxTemp = weeklyData.stream()
                                                 .map(WeatherDataDTO::getTemperature)
                                                 .max(Comparator.naturalOrder());
            maxTemp.ifPresent(val -> updateGaugeValue(weeklyMaxTemperatureHolders, DEFAULT_CITY, val));

            Optional<Double> minTemp = weeklyData.stream()
                                                 .map(WeatherDataDTO::getTemperature)
                                                 .min(Comparator.naturalOrder());
            minTemp.ifPresent(val -> updateGaugeValue(weeklyMinTemperatureHolders, DEFAULT_CITY, val));
        } else {
            logger.warn("No weekly data available to calculate min/max for {}. Setting to NaN.", DEFAULT_CITY);
            updateGaugeValue(weeklyMaxTemperatureHolders, DEFAULT_CITY, Double.NaN);
            updateGaugeValue(weeklyMinTemperatureHolders, DEFAULT_CITY, Double.NaN);
        }
        
        return averageTemps;
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
    
    private void updateGaugeValue(Map<String, DoubleHolder> holdersMap, String city, double value) {
        DoubleHolder holder = holdersMap.computeIfAbsent(city, k -> {
            logger.warn("Gauge for city {} was not pre-initialized for this metric. Initializing now.", city);
            DoubleHolder newHolder = new DoubleHolder(Double.NaN); // Valor inicial NaN
            // Lógica para inferir el nombre de la métrica basada en el mapa
            String metricName = "unknown.temperature.metric";
            if (holdersMap == currentTemperatureHolders) metricName = "weather.current.temperature";
            else if (holdersMap == averageTemperatureLastDayHolders) metricName = "weather.average.temperature.lastday";
            else if (holdersMap == averageTemperatureLastWeekHolders) metricName = "weather.average.temperature.lastweek";
            else if (holdersMap == dailyMaxTemperatureHolders) metricName = "weather.daily_temperature_max";
            else if (holdersMap == dailyMinTemperatureHolders) metricName = "weather.daily_temperature_min";
            else if (holdersMap == weeklyMaxTemperatureHolders) metricName = "weather.weekly_temperature_max";
            else if (holdersMap == weeklyMinTemperatureHolders) metricName = "weather.weekly_temperature_min";

            Gauge.builder(metricName, newHolder, DoubleHolder::getValue)
                    .tag("city", city)
                    .description("Temperature metric for " + city + ": " + metricName)
                    .register(meterRegistry);
            return newHolder;
        });
        holder.value(value);
    }
    
    private static class DoubleHolder {
        private double value;

        public DoubleHolder(double value) {
            this.value = value;
        }

        public double getValue() {
            return value;
        }

        public void value(double value) {
            this.value = value;
        }
    }
}
