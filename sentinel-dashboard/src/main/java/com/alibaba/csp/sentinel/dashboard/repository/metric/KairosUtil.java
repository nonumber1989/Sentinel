package com.alibaba.csp.sentinel.dashboard.repository.metric;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.MetricEntity;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.MetricBuilder;
import org.kairosdb.client.builder.QueryBuilder;
import org.kairosdb.client.builder.QueryMetric;
import org.kairosdb.client.response.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

public class KairosUtil {

    private static final Logger logger = LoggerFactory.getLogger(KairosUtil.class);

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
        Map<String, Object> metricMap = objectToMap(metric);
        //compose tags
        Map<String, String> tags = new HashMap<>();
        tags.put("app", metric.getApp());
        tags.put("resource", metric.getResource());

        //compose metric data point
        MetricBuilder builder = MetricBuilder.getInstance();
        SENTINEL_METRICS.stream().forEach(metricName -> {
            builder.addMetric(SENTINEL_PRIFIX + metricName)
                    .addTags(tags)
                    .addDataPoint(metric.getTimestamp().getTime(), metricMap.get(metricName));
        });
        KAIROS_HTTPCLIENT.pushMetrics(builder);
    }

    public static List<MetricEntity> queryFromKairosDB(String app, String resource, long startTime, long endTime) {

        QueryBuilder builder = QueryBuilder.getInstance();
        SENTINEL_METRICS.stream().forEach(metricName -> {
            QueryMetric queryMetric = new QueryMetric(SENTINEL_PRIFIX + metricName);
            queryMetric.addTag("app", app);
            queryMetric.addTag("resource", resource);
            builder.setStart(new Date(startTime))
                    .setEnd(new Date(endTime))
                    .addMetric(queryMetric);
        });
        QueryResponse response = KAIROS_HTTPCLIENT.query(builder);

        //I split two steps
        //First groupBy
        Map<Date, List<MetricEntity>> groupedMetrics = response.getQueries().stream().flatMap(query -> query.getResults().stream()).flatMap(result -> {
            String metricName = result.getName().substring(SENTINEL_PRIFIX.length());

            return result.getDataPoints().stream().map(dataPoint -> {
                MetricEntity entity = new MetricEntity();
                entity.setApp(app);
                entity.setResource(resource);
                entity.setTimestamp(new Date(dataPoint.getTimestamp()));
                setValueForMetricEntity(entity, metricName, dataPoint.getValue().toString());
                return entity;
            });
        }).collect(Collectors.groupingBy(MetricEntity::getTimestamp));


        //second step aggregate
        List<MetricEntity> metricEntities = groupedMetrics.values().stream().map(groupedMetric -> {
            Map<String, Object> metricMap = new HashMap<>();
            groupedMetric.stream().forEach(metric -> {
                Map<String, Object> tempMap = objectToMap(metric);
                SENTINEL_METRICS.stream().forEach(fieldName -> {
                    Object value = tempMap.get(fieldName);
                    if (Objects.nonNull(value)) {
                        metricMap.put(fieldName, value);
                    }
                });
            });
            MetricEntity metricEntity = mapToObject(metricMap, MetricEntity.class);
            metricEntity.setApp(app);
            metricEntity.setResource(resource);
            return metricEntity;
        }).collect(Collectors.toList());
        return metricEntities;
    }

    /**
     * just convert java object to map
     *
     * @param target
     * @return
     */
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

    /**
     * map to java object
     *
     * @param map
     * @param typeClass
     * @return
     */
    public static <T> T mapToObject(Map<String, Object> map, Class<T> typeClass) {
        T object = null;
        if (Objects.nonNull(map) && !map.isEmpty()) {
            try {
                BeanInfo beanInfo = Introspector.getBeanInfo(typeClass, Object.class);
                PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
                object = typeClass.newInstance();
                for (PropertyDescriptor property : propertyDescriptors) {
                    Method setter = property.getWriteMethod();
                    String propertyName = property.getName();
                    if (Objects.nonNull(setter) && map.containsKey(propertyName)) {
                        setter.invoke(object, map.get(propertyName));
                    }
                }
                return object;
            } catch (Exception e) {
                throw new RuntimeException("convert map to java object exception !", e);
            }
        }
        return object;
    }


    private static void setValueForMetricEntity(MetricEntity entity, final String metricName, final String stringValue) {
        String fieldName = metricName.substring(0, 1).toUpperCase() + metricName.substring(1);
        // all metric is number format , use reflect to parse
        try {
            Class metricEntityClass = entity.getClass();
            Field field = metricEntityClass.getDeclaredField(metricName);
            Class filedType = field.getType();
            Method method = metricEntityClass.getDeclaredMethod("set" + fieldName, filedType);
            Number number = NumberFormat.getInstance().parse(stringValue);
            method.invoke(entity, number);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        queryFromKairosDB("falcon", "/topics/1", 1578637628000L, 1578724029000L);
    }

}
