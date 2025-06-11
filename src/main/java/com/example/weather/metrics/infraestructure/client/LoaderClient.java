package com.example.weather.metrics.infraestructure.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "loader-client", url = "http://localhost:8085")
public interface LoaderClient {

    @GetMapping(path = "/current", produces = MediaType.APPLICATION_JSON_VALUE)
    WeatherDataDTO obtenerTemperaturaActual();
    
    @GetMapping(path = "/today", produces = MediaType.APPLICATION_JSON_VALUE)
    List<WeatherDataDTO> obtenerTemperaturasHoy();
    
    @GetMapping(path = "/last-week", produces = MediaType.APPLICATION_JSON_VALUE)
    List<WeatherDataDTO> obtenerTemperaturasUltimaSemana();

}
