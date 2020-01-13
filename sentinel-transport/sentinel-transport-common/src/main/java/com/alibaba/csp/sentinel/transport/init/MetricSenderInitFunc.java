package com.alibaba.csp.sentinel.transport.init;

import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.config.SentinelConfig;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.init.InitOrder;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.metric.MetricSenderProvider;
import com.alibaba.csp.sentinel.transport.MetricSender;
import com.alibaba.csp.sentinel.transport.config.TransportConfig;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy;
import java.util.concurrent.TimeUnit;

@InitOrder(-1)
public class MetricSenderInitFunc implements InitFunc {

    private ScheduledExecutorService pool = null;

    private void initSchedulerIfNeeded() {
        if (pool == null) {
            pool = new ScheduledThreadPoolExecutor(1,
                    new NamedThreadFactory("sentinel-metric-send-task", true),
                    new DiscardOldestPolicy());
        }
    }

    @Override
    public void init() {
        MetricSender sender = MetricSenderProvider.getMetricSender();
        if (sender == null) {
            RecordLog.warn("[MetricSenderInitFunc] WARN: No MetricSender loaded");
            return;
        }

        initSchedulerIfNeeded();
        long interval = retrieveInterval(sender);
        setIntervalIfNotExists(interval);
        scheduleMetricSendTask(sender, interval);
    }

    private boolean isValidInterval(Long interval) {
        return interval != null && interval > 0;
    }

    private void setIntervalIfNotExists(long interval) {
        SentinelConfig.setConfig(TransportConfig.HEARTBEAT_INTERVAL_MS, String.valueOf(interval));
    }

    long retrieveInterval(/*@NonNull*/ MetricSender sender) {
        Long intervalInConfig = TransportConfig.getMetricIntervalMs();
        if (isValidInterval(intervalInConfig)) {
            RecordLog.info("[MetricSenderInitFunc] Using metric interval "
                    + "in Sentinel config property: " + intervalInConfig);
            return intervalInConfig;
        } else {
            long senderInterval = sender.intervalMs();
            RecordLog.info("[MetricSenderInitFunc] metric interval not configured in "
                    + "config property or invalid, using sender default: " + senderInterval);
            return senderInterval;
        }
    }

    private void scheduleMetricSendTask(/*@NonNull*/ final MetricSender sender, /*@Valid*/ long interval) {
        pool.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    sender.sendMetric(System.currentTimeMillis());
                } catch (Throwable e) {
                    RecordLog.warn("[MetricSender] Send metric error", e);
                }
            }
        }, 5000, interval, TimeUnit.MILLISECONDS);
        RecordLog.info("[MetricSenderInit] MetricSender started: "
                + sender.getClass().getCanonicalName());
    }
}
