package com.example.weather.metrics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

@Service
public class WeatherMetricsService {

    private static final String KAFKA_TOPIC = "weather-data";
    private static final String REDIS_KEY_CURRENT_TEMP = "weather:currentTemp";
    private static final String REDIS_KEY_TEMP_LAST_DAY = "weather:temps:lastDay";
    private static final String REDIS_KEY_TEMP_LAST_WEEK = "weather:temps:lastWeek";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaConsumer<String, String> kafkaConsumer;
    private final JedisPool jedisPool;
    private volatile boolean running = true;

    private final int hours_per_day = 24; 
    private final int min_per_hour = 60; 
    private final int sec_per_min = 60; 
    
    private ITimeProvider timeProvider;

    @Autowired
    public WeatherMetricsService(KafkaConsumer<String, String> kafkaConsumer, JedisPool jedisPool) {
        this.kafkaConsumer = kafkaConsumer;
        this.jedisPool = jedisPool;
        this.timeProvider = TimeProvider.getInstance();
    }
    
    public WeatherMetricsService(KafkaConsumer<String, String> kafkaConsumer, JedisPool jedisPool, ITimeProvider timeProvider) {
        this.kafkaConsumer = kafkaConsumer;
        this.jedisPool = jedisPool;
        this.timeProvider = timeProvider != null ? timeProvider : TimeProvider.getInstance();
    }

    @PostConstruct
    public void start() {
        kafkaConsumer.subscribe(Collections.singletonList(KAFKA_TOPIC));
        new Thread(this::pollKafkaLoop).start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        kafkaConsumer.wakeup();
        kafkaConsumer.close();
    }
    
    private void pollKafkaLoop() {
        try {
            while (running) {
            	var records = kafkaConsumer.poll(Duration.ofMinutes(1));
                if (records.isEmpty()) {
                    System.out.println("No records received.");
                } else {
                    System.out.println("Received " + records.count() + " records.");
                    for (ConsumerRecord<String, String> record : records) {
                        processMessage(record.value());
                        printRedisData();
                    }
                }
            }
        } catch (Exception e) {
            if (running) {
                e.printStackTrace();
            }
        }
    }

    public void processMessage(String json) {
        try (Jedis jedis = jedisPool.getResource()) {
            JsonNode root = objectMapper.readTree(json);
            String city = root.path("name").asText();
            double temp = root.path("main").path("temp").asDouble();
            long timestamp = timeProvider.currentTimeMillis(); // Use timeProvider here

            // Guardar la temperatura actual en Redis
            jedis.hset(REDIS_KEY_CURRENT_TEMP, city, String.valueOf(temp));

            // Guardar temperaturas históricas en SortedSet
            String lastDayKey = REDIS_KEY_TEMP_LAST_DAY + ":" + city;
            String lastWeekKey = REDIS_KEY_TEMP_LAST_WEEK + ":" + city;
            
            jedis.zadd(lastDayKey, timestamp, String.valueOf(temp));
            jedis.zadd(lastWeekKey, timestamp, String.valueOf(temp));

            int secondPerDay = hours_per_day * min_per_hour * sec_per_min;

            // Eliminar entradas en los conjuntos ordenados (Sorted Sets) de Redis que ya no son relevantes para el cálculo de promedios.
            long oneWeekMillis = 7L * secondPerDay * 1000;
            long now = timeProvider.currentTimeMillis(); // Use timeProvider here
            jedis.zremrangeByScore(lastDayKey, 0, now - oneWeekMillis);
            jedis.zremrangeByScore(lastWeekKey, 0, now - oneWeekMillis);
            
            // Evita almacenamiento infinito
            jedis.expire(lastDayKey, secondPerDay * 1); // segundos al dia x 1 día
            jedis.expire(lastWeekKey, secondPerDay * 1); // segundos al dia x 1 día
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, Double> getCurrentTemperatures() {
        try (Jedis jedis = jedisPool.getResource()) {
        	Map<String, String> temps = jedis.hgetAll(REDIS_KEY_CURRENT_TEMP);
            System.out.println("Current temperatures from Redis: " + temps);
            
            return jedis.hgetAll(REDIS_KEY_CURRENT_TEMP).entrySet().stream()
                    .collect(TreeMap::new, (m, e) -> m.put(e.getKey(), Double.parseDouble(e.getValue())), Map::putAll);
        }
    }
    
	private void printRedisData() {
	    try (Jedis jedis = jedisPool.getResource()) {
	        // Imprimir temperaturas actuales
	        Map<String, String> currentTemps = jedis.hgetAll(REDIS_KEY_CURRENT_TEMP);
	        System.out.println("Current Temperatures: " + currentTemps);
	        
	        // Imprimir temperaturas del último día
	        for (String city : currentTemps.keySet()) {
	            List<String> lastDayTemps = jedis.zrange(REDIS_KEY_TEMP_LAST_DAY + ":" + city, 0, -1);
	            System.out.println("Last Day Temperatures for " + city + ": " + lastDayTemps);
	        }
	        
	        // Imprimir temperaturas de la última semana
	        for (String city : currentTemps.keySet()) {
	            List<String> lastWeekTemps = jedis.zrange(REDIS_KEY_TEMP_LAST_WEEK + ":" + city, 0, -1);
	            System.out.println("Last Week Temperatures for " + city + ": " + lastWeekTemps);
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}
	
    public Map<String, Double> getAverageTemperatureLastDay() {
        return getAverageTemperatureForPeriod(24 * 60 * 60 * 1000L);
    }

    public Map<String, Double> getAverageTemperatureLastWeek() {
        return getAverageTemperatureForPeriod(7 * 24 * 60 * 60 * 1000L);
    }

    private Map<String, Double> getAverageTemperatureForPeriod(long periodMillis) {
        Map<String, Double> averages = new TreeMap<>();
        
        try (Jedis jedis = jedisPool.getResource()) {
        	int seconds = hours_per_day * min_per_hour * sec_per_min;
            String keyPattern = (periodMillis == seconds * 1000L) ? REDIS_KEY_TEMP_LAST_DAY + ":*" : REDIS_KEY_TEMP_LAST_WEEK + ":*";

            var keys = jedis.keys(keyPattern);

            long now = timeProvider.currentTimeMillis(); 

            for (String key : keys) {
                String city = key.substring(key.indexOf(":") + 1);
                long timeFrom = now - periodMillis;

                System.out.println("Service: Processing key: " + key + ", City: " + city); 
                
                var tempsStr = jedis.zrangeByScore(key, timeFrom, now);
                var temps = tempsStr.stream().map(Double::parseDouble).toList();

                if (!temps.isEmpty()) {
                    double avg = temps.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
                    averages.put(city, avg);
                    System.out.println("Service: Added average for " + city + ": " + avg); 
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return averages;
    }
}
