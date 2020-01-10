package com.alibaba.csp.sentinel.dashboard.domain.vo;

import com.alibaba.csp.sentinel.node.metric.MetricNode;

import java.util.List;

public class MetricNodesVo {
    private List<MetricNode> metricNodes;
    //TODO just for test
    private String ip;
    private String app;

    public List<MetricNode> getMetricNodes() {
        return metricNodes;
    }

    public void setMetricNodes(List<MetricNode> metricNodes) {
        this.metricNodes = metricNodes;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }
}
