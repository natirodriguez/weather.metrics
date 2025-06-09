package com.example.weather.metrics;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import com.example.weather.metrics.service.ITimeProvider;
import com.example.weather.metrics.service.WeatherMetricsService;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class WeatherMetricsServiceUnitTest {
    private static final String REDIS_KEY_TEMP_LAST_DAY = "weather:temps:lastDay";
    private static final String REDIS_KEY_TEMP_LAST_WEEK = "weather:temps:lastWeek";

    @Mock
    private JedisPool jedisPool;

    @Mock
    private Jedis jedis;

    @Mock
    private ITimeProvider timeProvider;

    private WeatherMetricsService weatherMetricsService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(jedisPool.getResource()).thenReturn(jedis);

        // Create service with mocked TimeProvider
        weatherMetricsService = new WeatherMetricsService(null, jedisPool, timeProvider);
    }

    @Test
    public void testProcessMessageRemovesOldEntriesKeepsNewEntries() throws Exception {
        String city = "TestCity";
        double temp = 22.5;
        String json = String.format("{\"name\":\"%s\",\"main\":{\"temp\":%.1f}}", city, temp);
        
        // Define times:
        // "now" is fixed current time for test
        long now = 1_600_000_000_000L; // Example fixed current time (Sep 2020)

        long oneWeekMillis = 7L * 24 * 60 * 60 * 1000;
        long oneDayMillis = 24L * 60 * 60 * 1000; // For comparison with expected expire time

        // Mock timeProvider to return fixed now for calls
        when(timeProvider.currentTimeMillis()).thenReturn(now);
        
        // Mock zadd to return 1 (indicating one element added)
        when(jedis.zadd(anyString(), anyDouble(), anyString())).thenReturn(1L);
        when(jedis.hset(anyString(), anyString(), anyString())).thenReturn(1L);
        
        // Mock zremrangeByScore to return the number of removed elements
        when(jedis.zremrangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(1L); // Example: 1 element removed

        // Call method under test
        weatherMetricsService.processMessage(json);

        // Verify hset called to store current temp
        verify(jedis).hset(eq("weather:currentTemp"), eq(city), eq(String.valueOf(temp)));

        // Verify zadd called twice for lastDay and lastWeek keys with current timestamp from TimeProvider
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);

        verify(jedis, times(2)).zadd(anyString(), scoreCaptor.capture(), eq(String.valueOf(temp)));

        // All scores passed in zadd should match "now" because processMessage uses current time for adds
        for (Double score : scoreCaptor.getAllValues()) {
            assert score.longValue() == now : "zadd score must match current time";
        }

        // Verify zremrangeByScore called twice to remove entries older than one week
        ArgumentCaptor<String> zremKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> zremMinScoreCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> zremMaxScoreCaptor = ArgumentCaptor.forClass(Double.class);

        verify(jedis, times(2)).zremrangeByScore(zremKeyCaptor.capture(), zremMinScoreCaptor.capture(), zremMaxScoreCaptor.capture());

        // min is always 0, max is now - 1 week millis
        for (int i = 0; i < 2; i++) {
            String key = zremKeyCaptor.getAllValues().get(i);
            Double minScore = zremMinScoreCaptor.getAllValues().get(i);
            Double maxScore = zremMaxScoreCaptor.getAllValues().get(i);

            // Check key format
            assert key.startsWith("weather:temps:last") : "zremrangeByScore key format";
            assert minScore == 0 : "zremrangeByScore minScore should be 0";
            // The score should be now - oneWeekMillis. Use a delta for double comparison if needed, but longValue() is fine here.
            assert maxScore.longValue() == (now - oneWeekMillis) : "zremrangeByScore maxScore should be now - 1 week millis. Expected: " + (now - oneWeekMillis) + ", Actual: " + maxScore.longValue();
        }

        // Check expire called twice with expected TTL (1 day in seconds)
        long expectedExpireSec = 24L * 60 * 60; // 1 day in seconds (as a long)
        
        // Corrected verification for expire: expect a long
        verify(jedis, times(2)).expire(startsWith("weather:temps:last"), eq(expectedExpireSec));

        System.out.println("Test passed: processMessage keeps new entries and removes entries older than 1 week.");
    }

    @Test
    public void testGetAverageTemperatureLastDayCalculatesCorrectlyWithMockedData() throws Exception {
        String city = "TestCity";
        long now = 1_600_000_000_000L; // Tiempo actual mockeado
        long oneDayMillis = 24L * 60 * 60 * 1000;
        
        when(timeProvider.currentTimeMillis()).thenReturn(now);
        
        Set<String> keys = new HashSet<>(Arrays.asList(REDIS_KEY_TEMP_LAST_DAY + ":" + city));
        when(jedis.keys(eq(REDIS_KEY_TEMP_LAST_DAY + ":*"))).thenReturn(keys);

        List<String> tempsForLastDay = Arrays.asList(String.valueOf(7.8), String.valueOf(11.1), String.valueOf(5.0));
        double expectedAverage = (7.8 + 11.1 + 5.0) / 3.0; 

        when(jedis.zrangeByScore(
                eq(REDIS_KEY_TEMP_LAST_DAY + ":" + city),
                eq((double)(now - oneDayMillis)),
                eq((double)now)
        )).thenReturn(tempsForLastDay);
        
        String tempLastDay = "temps:lastDay:"+ city;
        Map<String, Double> averages = weatherMetricsService.getAverageTemperatureLastDay();

        assert averages.size() == 1 : "Debería haber un promedio para una ciudad";
        assert Math.abs(averages.get(tempLastDay) - expectedAverage) < 0.001 : "La temperatura promedio debería ser 22.5. Esperado: 22.5, Actual: " + averages.get(city); // (20.0 + 25.0) / 2

        verify(jedis).keys(eq(REDIS_KEY_TEMP_LAST_DAY + ":*"));
        verify(jedis).zrangeByScore(
            eq(REDIS_KEY_TEMP_LAST_DAY + ":" + city),
            eq((double)(now - oneDayMillis)),
            eq((double)now)
        );

        System.out.println("Test passed: getAverageTemperatureLastDay calcula promedios correctamente con datos mockeados.");
    }
    
    @Test
    public void testGetAverageTemperatureLastWeekCalculatesCorrectlyWithMockedData() throws Exception {
        String city = "TestCity";
        long now = 1_600_000_000_000L; 
        long oneWeekMillis = 7L * 24 * 60 * 60 * 1000; 
        
        when(timeProvider.currentTimeMillis()).thenReturn(now);
        
        Set<String> keys = new HashSet<>(Arrays.asList(REDIS_KEY_TEMP_LAST_WEEK + ":" + city));
        when(jedis.keys(eq(REDIS_KEY_TEMP_LAST_WEEK + ":*"))).thenReturn(keys);

        List<Double> allTemperatures = new ArrayList<>();
        allTemperatures.addAll(Arrays.asList(0.5, 1.2));        // Día 1
        allTemperatures.addAll(Arrays.asList(-2.0, 0.0, 3.5));  // Día 2
        allTemperatures.addAll(Arrays.asList(-1.5, 2.0));       // Día 3
        allTemperatures.addAll(Arrays.asList(1.0, 4.0, 0.0));   // Día 4
        allTemperatures.addAll(Arrays.asList(2.5, 5.0));        // Día 5
        allTemperatures.addAll(Arrays.asList(3.0, 6.0, 4.5));   // Día 6
        allTemperatures.addAll(Arrays.asList(5.5, 7.0));        // Día 7 (el día 'now')

        List<String> tempsForLastWeek = allTemperatures.stream()
                                                    .map(String::valueOf)
                                                    .toList();
        
        double expectedAverage = allTemperatures.stream()
                                            .mapToDouble(Double::doubleValue)
                                            .average()
                                            .orElse(Double.NaN); // Si la lista está vacía, devuelve NaN

        when(jedis.zrangeByScore(
                eq(REDIS_KEY_TEMP_LAST_WEEK + ":" + city), // La clave completa para la semana
                eq((double)(now - oneWeekMillis)),         // Rango de tiempo: inicio de la semana
                eq((double)now)                            // Rango de tiempo: fin de la semana
        )).thenReturn(tempsForLastWeek);
        
        String expectedMapKey = "temps:lastWeek:" + city; 
        
        Map<String, Double> averages = weatherMetricsService.getAverageTemperatureLastWeek();

        assert averages.size() == 1 : "Debería haber un promedio para una ciudad. Tamaño: " + averages.size();
        assert averages.containsKey(expectedMapKey) : "El mapa debería contener la clave: " + expectedMapKey + ". Claves actuales: " + averages.keySet();
        
        Double actualAverage = averages.get(expectedMapKey);
        assert actualAverage != null : "El promedio para la ciudad '" + expectedMapKey + "' no debería ser null.";
        assert Math.abs(actualAverage - expectedAverage) < 0.001 : 
            "La temperatura promedio de la semana debería ser " + expectedAverage + ". Esperado: " + expectedAverage + ", Actual: " + actualAverage;

        verify(jedis).keys(eq(REDIS_KEY_TEMP_LAST_WEEK + ":*"));
        verify(jedis).zrangeByScore(
            eq(REDIS_KEY_TEMP_LAST_WEEK + ":" + city),
            eq((double)(now - oneWeekMillis)),
            eq((double)now)
        );

        System.out.println("Test passed: getAverageTemperatureLastWeek calcula promedios correctamente con datos mockeados de invierno.");
    }
}
