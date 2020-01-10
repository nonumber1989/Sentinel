package com.alibaba.csp.sentinel.transport;

public interface MetricSender {
    boolean sendMetric(Long currentTime);

    long intervalMs();
}
