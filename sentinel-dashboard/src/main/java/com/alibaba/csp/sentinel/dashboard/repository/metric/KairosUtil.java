package com.alibaba.csp.sentinel.dashboard.repository.metric;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.MetricEntity;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.MetricBuilder;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.*;

public class KairosUtil {
    public static HttpClient KAIROS_HTTPCLIENT;

    //kairosDB address
    public static final String KARIOSDB_ADDRESS = "kairosdb.address";
    public static final String SENTINEL_PRIFIX = "sentinel_";
    //TODO
    public static final List<String> SENTINEL_METRICS = Arrays.asList("passQps", "successQps", "blockQps", "exceptionQps", "rt");

    static {
        try {
//            String kairosAddress = System.getProperty(KARIOSDB_ADDRESS);
            String kairosAddress = "http://localhost:10101";
            if (Objects.isNull(kairosAddress)) {
                throw new RuntimeException("kairosdb.address property must defined first !");
            }
            KAIROS_HTTPCLIENT = new HttpClient(kairosAddress);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public static void writeToKairosDB(MetricEntity metric) {
        SENTINEL_METRICS.stream().forEach(element -> {
            writeToKairosDB(element, metric);
        });
    }

    public static void writeToKairosDB(String metricName, MetricEntity metric) {
        Map<String, Object> metricMap = objectToMap(metric);
        //compose tags
        Map<String, String> tags = new HashMap<>();
        tags.put("app", metric.getApp());
        tags.put("resource", metric.getResource());

        //compose metric data point
        MetricBuilder builder = MetricBuilder.getInstance();
        builder.addMetric(SENTINEL_PRIFIX + metricName)
                .addTags(tags)
                .addDataPoint(metric.getTimestamp().getTime(), metricMap.get(metricName));
        KAIROS_HTTPCLIENT.pushMetrics(builder);
    }

    public static Map<String, Object> objectToMap(Object target) {
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            BeanInfo info = Introspector.getBeanInfo(target.getClass());
            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
                Method reader = pd.getReadMethod();
                if (Objects.nonNull(reader)) {
                    result.put(pd.getName(), reader.invoke(target));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("convert java object to map exception !", e);
        }
        return result;
    }

    public static void main(String[] args) {
        MetricEntity metricEntity = new MetricEntity();
        metricEntity.setApp("falcon");
        metricEntity.setResource("/path/get");
        metricEntity.setBlockQps(1000L);
        metricEntity.setPassQps(200L);
        metricEntity.setSuccessQps(200L);
        metricEntity.setExceptionQps(190L);
        metricEntity.setRt(100.20);
        writeToKairosDB(metricEntity);
    }
}
