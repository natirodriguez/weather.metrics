package com.example.weather.metrics.service;

public class TimeProvider implements ITimeProvider {
    private static final TimeProvider INSTANCE = new TimeProvider();
    private TimeProvider() {}
    public static TimeProvider getInstance() {
        return INSTANCE;
    }
    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
