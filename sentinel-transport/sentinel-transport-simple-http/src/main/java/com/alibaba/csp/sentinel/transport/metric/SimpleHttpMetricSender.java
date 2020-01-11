package com.alibaba.csp.sentinel.transport.metric;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.command.CommandResponse;
import com.alibaba.csp.sentinel.config.SentinelConfig;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.node.metric.MetricNode;
import com.alibaba.csp.sentinel.node.metric.MetricSearcher;
import com.alibaba.csp.sentinel.node.metric.MetricWriter;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.csp.sentinel.transport.MetricSender;
import com.alibaba.csp.sentinel.transport.config.TransportConfig;
import com.alibaba.csp.sentinel.transport.heartbeat.client.SimpleHttpClient;
import com.alibaba.csp.sentinel.transport.heartbeat.client.SimpleHttpRequest;
import com.alibaba.csp.sentinel.transport.heartbeat.client.SimpleHttpResponse;
import com.alibaba.csp.sentinel.util.AppNameUtil;
import com.alibaba.csp.sentinel.util.PidUtil;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.fastjson.JSONObject;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SimpleHttpMetricSender implements MetricSender {
    private static final int OK_STATUS = 200;

    private static final long DEFAULT_INTERVAL = 1000 * 10;

    private static final int MAX_LINES = 12000;

    private final SimpleHttpClient httpClient = new SimpleHttpClient();

    private final List<InetSocketAddress> addressList;

    private int currentAddressIdx = 0;

    private MetricSearcher searcher;

    private final Object lock = new Object();

    public SimpleHttpMetricSender() {
        // Retrieve the list of default addresses.
        List<InetSocketAddress> newAddrs = getDefaultConsoleIps();
        RecordLog.info("[SimpleHttpMetricSender] Default console address list retrieved: " + newAddrs);
        this.addressList = newAddrs;

        //initial metricSearcher
        if (searcher == null) {
            synchronized (lock) {
                String appName = SentinelConfig.getAppName();
                if (appName == null) {
                    appName = "";
                }
                if (searcher == null) {
                    searcher = new MetricSearcher(MetricWriter.METRIC_BASE_DIR, MetricWriter.formMetricFileName(appName, PidUtil.getPid()));
                }
            }
        }
    }

    @Override
    public boolean sendMetric(Long currentTime) {
        if (TransportConfig.getRuntimePort() <= 0) {
            RecordLog.info("[SimpleHttpMetricSender] Runtime port not initialized, won't send metric");
            return false;
        }
        InetSocketAddress addr = getAvailableAddress();
        if (addr == null) {
            return false;
        }

        SimpleHttpRequest request = new SimpleHttpRequest(addr, TransportConfig.getMetricApiPath());
        List<MetricNode> metricNodes = searchMetricNodes(currentTime);
        JSONObject jsonNode = new JSONObject();
        jsonNode.put("metricNodes", metricNodes);
        jsonNode.put("app", AppNameUtil.getAppName());
        request.setJson(jsonNode.toJSONString());
        try {
            SimpleHttpResponse response = httpClient.postJSON(request);
            if (response.getStatusCode() == OK_STATUS) {
                return true;
            }
        } catch (Exception e) {
            RecordLog.warn("[SimpleHttpMetricSender] Failed to send metric to " + addr, e);
        }
        return false;
    }

    @Override
    public long intervalMs() {
        return DEFAULT_INTERVAL;
    }

    private InetSocketAddress getAvailableAddress() {
        if (addressList == null || addressList.isEmpty()) {
            return null;
        }
        if (currentAddressIdx < 0) {
            currentAddressIdx = 0;
        }
        int index = currentAddressIdx % addressList.size();
        return addressList.get(index);
    }

    private List<InetSocketAddress> getDefaultConsoleIps() {
        List<InetSocketAddress> newAddrs = new ArrayList<InetSocketAddress>();
        try {
            String ipsStr = TransportConfig.getConsoleServer();
            if (StringUtil.isEmpty(ipsStr)) {
                RecordLog.warn("[SimpleHttpHeartbeatSender] Dashboard server address not configured");
                return newAddrs;
            }

            for (String ipPortStr : ipsStr.split(",")) {
                if (ipPortStr.trim().length() == 0) {
                    continue;
                }
                if (ipPortStr.startsWith("http://")) {
                    ipPortStr = ipPortStr.trim().substring(7);
                }
                String[] ipPort = ipPortStr.trim().split(":");
                int port = 80;
                if (ipPort.length > 1) {
                    port = Integer.parseInt(ipPort[1].trim());
                }
                newAddrs.add(new InetSocketAddress(ipPort[0].trim(), port));
            }
        } catch (Exception ex) {
            RecordLog.warn("[SimpleHeartbeatSender] Parse dashboard list failed, current address list: " + newAddrs, ex);
            ex.printStackTrace();
        }
        return newAddrs;
    }


    public List<MetricNode> searchMetricNodes(Long currentTime) {
        List<MetricNode> metricNodes = null;
        Long startTime = currentTime - DEFAULT_INTERVAL;
        try {
            metricNodes = searcher.find(startTime, MAX_LINES);
        } catch (Exception ex) {
            CommandResponse.ofFailure(new RuntimeException("Error when retrieving metrics", ex));
        }
        if(metricNodes == null){
            metricNodes = new ArrayList<>();
        }
        addCpuUsageAndLoad(metricNodes);
        return metricNodes;
    }

    /**
     * add current cpu usage and load to the metric list.
     *
     * @param list metric list, should not be null
     */
    private void addCpuUsageAndLoad(List<MetricNode> list) {
        long time = TimeUtil.currentTimeMillis() / 1000 * 1000;
        double load = SystemRuleManager.getCurrentSystemAvgLoad();
        double usage = SystemRuleManager.getCurrentCpuUsage();
        if (load > 0) {
            MetricNode loadNode = toNode(load, time, Constants.SYSTEM_LOAD_RESOURCE_NAME);
            list.add(loadNode);
        }
        if (usage > 0) {
            MetricNode usageNode = toNode(usage, time, Constants.CPU_USAGE_RESOURCE_NAME);
            list.add(usageNode);
        }
    }

    /**
     * transfer the value to a MetricNode, the value will multiply 10000 then truncate
     * to long value, and as the {@link MetricNode}.
     * <p>
     * This is an eclectic scheme before we have a standard metric format.
     * </p>
     *
     * @param value    value to save.
     * @param ts       timestamp
     * @param resource resource name.
     * @return a MetricNode represents the value.
     */
    private MetricNode toNode(double value, long ts, String resource) {
        MetricNode node = new MetricNode();
        node.setPassQps((long) (value * 10000));
        node.setTimestamp(ts);
        node.setResource(resource);
        return node;
    }
}
