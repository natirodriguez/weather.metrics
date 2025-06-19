package com.example.weather.metrics.component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

@Component
public class HardwareMetrics {

    private final OperatingSystemMXBean osBean;

    public HardwareMetrics(MeterRegistry meterRegistry) {
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        // Memory used by JVM (heap usage)
        Gauge.builder("jvm.memory.used", 
            () -> (double)(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()))
            .description("Memoria utilizada por la JVM en bytes")
            .register(meterRegistry);

        // System CPU usage as a double between 0 and 1
        Gauge.builder("system.cpu.usage", 
            () -> getSystemCpuUsage())
            .description("Uso de CPU del sistema (0.0 - 1.0)")
            .register(meterRegistry);
    }

    private double getSystemCpuUsage() {
        double cpuLoad = osBean.getSystemCpuLoad();
        // getSystemCpuLoad() returns a value between 0.0 and 1.0 or negative if not available
        return (cpuLoad >= 0) ? cpuLoad : 0.0;
    }
}


