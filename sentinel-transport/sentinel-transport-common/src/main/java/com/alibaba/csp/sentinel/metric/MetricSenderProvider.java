package com.alibaba.csp.sentinel.metric;

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.transport.MetricSender;
import com.alibaba.csp.sentinel.util.SpiLoader;

public final class MetricSenderProvider {
    private static MetricSender metricSender = null;

    static {
        resolveInstance();
    }

    private static void resolveInstance() {
        MetricSender resolved = SpiLoader.loadHighestPriorityInstance(MetricSender.class);
        if (resolved == null) {
            RecordLog.warn("[MetricSenderProvider] WARN: No existing MetricSender found");
        } else {
            metricSender = resolved;
            RecordLog.info("[MetricSenderProvider] MetricSender activated: " + resolved.getClass().getCanonicalName());
        }
    }

    /**
     * Get resolved {@link MetricSender} instance.
     *
     * @return resolved {@code MetricSender} instance
     */
    public static MetricSender getMetricSender() {
        return metricSender;
    }

    private MetricSenderProvider() {
    }
}
