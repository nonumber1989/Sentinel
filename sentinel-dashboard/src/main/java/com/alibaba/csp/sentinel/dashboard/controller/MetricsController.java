package com.alibaba.csp.sentinel.dashboard.controller;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.MetricEntity;
import com.alibaba.csp.sentinel.dashboard.discovery.MachineInfo;
import com.alibaba.csp.sentinel.dashboard.domain.Result;
import com.alibaba.csp.sentinel.dashboard.domain.vo.MetricNodesVo;
import com.alibaba.csp.sentinel.dashboard.metric.MetricFetcher;
import com.alibaba.csp.sentinel.dashboard.repository.metric.MetricsRepository;
import com.alibaba.csp.sentinel.node.metric.MetricNode;
import com.alibaba.csp.sentinel.util.StringUtil;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.util.*;

/**
 * only for client push  metrics to server side
 * query metrics function please refer MetricController
 */
@Controller
@RequestMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
public class MetricsController {

    private static Logger logger = LoggerFactory.getLogger(MetricsController.class);

    @Autowired
    private MetricFetcher metricFetcher;

    @Autowired
    private MetricsRepository<MetricEntity> metricStore;

    @PostMapping
    public Result<?> pushMetrics(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null) {
            ipAddress = request.getRemoteAddr();
        }
        String appName = request.getHeader("X-SENTINEL-APP");
        MachineInfo machineInfo = new MachineInfo();
        machineInfo.setApp(appName);
        machineInfo.setIp(ipAddress);
        return Result.ofSuccess(null);
    }

    @PostMapping(value = "/metricNodes")
    public Result<?> pushMetricNodes(@RequestBody MetricNodesVo metricNodes, HttpServletRequest request) {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null) {
            ipAddress = request.getRemoteAddr();
        }
        String appName = request.getHeader("X-SENTINEL-APP");
        MachineInfo machineInfo = new MachineInfo();
        machineInfo.setApp(appName);
        machineInfo.setIp(ipAddress);
        Map<String, MetricEntity> metricMap =  handleMetricNodes(metricNodes.getMetricNodes(),machineInfo);
        metricStore.saveAll(metricMap.values());
        return Result.ofSuccess(null);
    }



    public Map<String, MetricEntity> handleMetricNodes(List<MetricNode> metricNodes, MachineInfo machine) {
        Map<String, MetricEntity> resultMap = new HashMap<>();
        for (MetricNode node : metricNodes) {
            if (metricFetcher.shouldFilterOut(node.getResource())) {
                continue;
            }
            /*
             * aggregation metrics by app_resource_timeSecond, ignore ip and port.
             */
            String key = metricFetcher.buildMetricKey(machine.getApp(), node.getResource(), node.getTimestamp());
            Date date = new Date();
            MetricEntity entity = new MetricEntity();
            entity.setApp(machine.getApp());
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

}
