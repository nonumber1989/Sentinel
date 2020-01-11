package com.alibaba.csp.sentinel.dashboard.repository.metric;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.KairosApplicationEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.MetricEntity;
import com.alibaba.csp.sentinel.dashboard.discovery.ResourceDiscovery;
import com.alibaba.csp.sentinel.dashboard.util.KairosUtil;
import com.alibaba.csp.sentinel.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component("kairosMetricsRepository")
public class KairosMetricsRepository implements MetricsRepository<MetricEntity> {
    @Autowired
    private ResourceDiscovery resourceDiscovery;

    @Override
    public void save(MetricEntity metric) {
        if (Objects.isNull(metric) || StringUtil.isBlank(metric.getApp())) {
            return;
        }
        KairosUtil.writeToKairosDB(metric);
    }

    @Override
    public void saveAll(Iterable<MetricEntity> metrics) {
        if (Objects.isNull(metrics)) {
            return;
        }
        metrics.forEach(this::save);
    }

    @Override
    public List<MetricEntity> queryByAppAndResourceBetween(String app, String resource, long startTime, long endTime) {
        return KairosUtil.queryFromKairosDB(app, resource, startTime, endTime);
    }

    @Override
    public List<String> listResourcesOfApp(String app) {
        KairosApplicationEntity kairosApplication = KairosUtil.KAIROS_APPLICATION.get(app);
        if (Objects.nonNull(kairosApplication)) {
            return new ArrayList<>(kairosApplication.getResources());
        }
        return Collections.emptyList();
    }
}
