package com.alibaba.csp.sentinel.dashboard.controller;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.MetricEntity;
import com.alibaba.csp.sentinel.dashboard.discovery.MachineInfo;
import com.alibaba.csp.sentinel.dashboard.domain.Result;
import com.alibaba.csp.sentinel.dashboard.domain.vo.MetricNodesVo;
import com.alibaba.csp.sentinel.dashboard.repository.metric.MetricsRepository;
import com.alibaba.csp.sentinel.node.metric.MetricNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * only for client push  metrics to server side
 * query metrics function please refer MetricController
 */
@RestController
@RequestMapping(value = "/metrics")
public class MetricsController {

    private static Logger logger = LoggerFactory.getLogger(MetricsController.class);

    private static final Set<String> RES_EXCLUSION_SET = new HashSet<String>() {{
        add(Constants.TOTAL_IN_RESOURCE_NAME);
        add(Constants.SYSTEM_LOAD_RESOURCE_NAME);
        add(Constants.CPU_USAGE_RESOURCE_NAME);
    }};

    @Autowired
    @Qualifier("kairosMetricsRepository")
    private MetricsRepository<MetricEntity> metricStore;

    @PostMapping
    public Result<?> pushMetricNodes(@RequestBody MetricNodesVo metricNodes, HttpServletRequest request) {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null) {
            ipAddress = request.getRemoteAddr();
        }
        MachineInfo machineInfo = new MachineInfo();
        machineInfo.setApp(metricNodes.getApp());
        machineInfo.setIp(ipAddress);
        List<MetricNode> metrics = metricNodes.getMetricNodes();
        handleMetricsWithPattern(metrics);
        Map<String, MetricEntity> metricMap = handleMetricNodes(metrics, machineInfo);
        metricStore.saveAll(metricMap.values());
        return Result.ofSuccess(null);
    }

    private List<MetricNode> handleMetricsWithPattern(List<MetricNode> metrics) {
        Map<Long, List<MetricNode>> groupMetrics = metrics.stream().collect(Collectors.groupingBy(MetricNode::getTimestamp));
        groupMetrics.entrySet().stream().forEach(element -> {
            List<String> allResources = element.getValue().stream().map(value -> value.getResource()).collect(Collectors.toList());
            //TODO just simple judge now
            Set<String> patternResources = allResources.stream().filter(resource -> resource.contains("/{")).collect(Collectors.toSet());
            if (!patternResources.isEmpty()) {
                Iterator<MetricNode> metricNodeIterator = metrics.iterator();
                while (metricNodeIterator.hasNext()) {
                    MetricNode node = metricNodeIterator.next();
                    boolean isPatternMatch = MetricsResourcePattern.isPatternMatch(node.getResource(), patternResources);
                    if (isPatternMatch) {
                        metricNodeIterator.remove();
                    }
                }
            }
        });
        return metrics;
    }

    public Map<String, MetricEntity> handleMetricNodes(List<MetricNode> metricNodes, MachineInfo machine) {
        Map<String, MetricEntity> resultMap = new HashMap<>();
        for (MetricNode node : metricNodes) {
            if (RES_EXCLUSION_SET.contains(node.getResource())) {
                continue;
            }
            /*
             * aggregation metrics by app_resource_timeSecond, ignore ip and port.
             */
            String key = buildMetricKey(machine.getApp(), node.getResource(), node.getTimestamp());
            Date date = new Date();
            MetricEntity entity = new MetricEntity();
            entity.setApp(machine.getApp());
            entity.setIp(machine.getIp());
            entity.setTimestamp(new Date(node.getTimestamp()));
            entity.setPassQps(node.getPassQps());
            entity.setBlockQps(node.getBlockQps());
            entity.setRtAndSuccessQps(node.getRt(), node.getSuccessQps());
            entity.setExceptionQps(node.getExceptionQps());
            entity.setCount(1);
            entity.setResource(node.getResource());
            entity.setGmtCreate(date);
            entity.setGmtModified(date);
            resultMap.put(key, entity);
        }
        return resultMap;
    }

    public String buildMetricKey(String app, String resource, long timestamp) {
        return app + "__" + resource + "__" + (timestamp / 1000);
    }

}
