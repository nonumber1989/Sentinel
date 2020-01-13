package com.alibaba.csp.sentinel.dashboard.util;

import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.KairosApplicationEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.MetricEntity;
import com.alibaba.csp.sentinel.dashboard.discovery.AppInfo;
import com.alibaba.csp.sentinel.dashboard.discovery.MachineDiscovery;
import com.alibaba.csp.sentinel.dashboard.discovery.ResourceDiscovery;
import com.google.gson.Gson;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.StandardHttpRequestRetryHandler;
import org.apache.http.util.EntityUtils;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.MetricBuilder;
import org.kairosdb.client.builder.QueryBuilder;
import org.kairosdb.client.builder.QueryMetric;
import org.kairosdb.client.response.QueryResponse;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class KairosUtil {

    private static ExecutorService KAIROS_EXECUTOR_SERVICE = new ThreadPoolExecutor(5, 5, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(1024), new NamedThreadFactory("sentinel-kairos-metrics-task"));

    public static final String SENTINEL_METADATA_SERVICE_NAME = "sentinel.metadata";
    // full path format is  /api/v1/metadata/{service}/{serviceKey}/{key}
    public static final String METADATA_SERVICE_PATH = "/api/v1/metadata/" + SENTINEL_METADATA_SERVICE_NAME + "/";

    private static HttpClientBuilder HTTPCLIENT_BUILDER = HttpClientBuilder.create().setRetryHandler(new StandardHttpRequestRetryHandler());

    private static CloseableHttpClient RAW_HTTPCLIENT = HTTPCLIENT_BUILDER.build();

    private static Gson GSON = new Gson();

    public static final String SENTINEL_PRIFIX = "sentinel.";
    //sentinel metrics
    public static final List<String> SENTINEL_METRICS = Arrays.asList("passQps", "successQps", "blockQps", "exceptionQps", "rt");

    public static void writeToKairosDB(HttpClient kairosHttpClient, MetricEntity metric) {
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
        kairosHttpClient.pushMetrics(builder);
    }

    public static List<MetricEntity> queryFromKairosDB(HttpClient kairosHttpClient, String app, String resource, long startTime, long endTime) {

        QueryBuilder builder = QueryBuilder.getInstance();
        SENTINEL_METRICS.stream().forEach(metricName -> {
            QueryMetric queryMetric = new QueryMetric(SENTINEL_PRIFIX + metricName);
            queryMetric.addTag("app", app);
            queryMetric.addTag("resource", resource);
            builder.setStart(new Date(startTime))
                    .setEnd(new Date(endTime))
                    .addMetric(queryMetric);
        });
        QueryResponse response = kairosHttpClient.query(builder);

        //I split two steps
        //First groupBy
        Map<Date, List<MetricEntity>> groupedMetrics = response.getQueries().stream()
                .flatMap(query -> query.getResults().stream())
                .flatMap(result -> {
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
        List<MetricEntity> metricEntities = groupedMetrics.entrySet().stream()
                .map(groupedMetric -> {
                    List<MetricEntity> metricValue = groupedMetric.getValue();
                    Date timestamp = groupedMetric.getKey();
                    Map<String, Object> metricMap = new HashMap<>();
                    metricValue.stream().forEach(metric -> {
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
                    metricEntity.setTimestamp(timestamp);
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


    public static boolean saveKairosMetadata(String url, String jsonBody) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(jsonBody, "UTF-8"));
        try (CloseableHttpResponse response = RAW_HTTPCLIENT.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 204) {
                return true;
            } else {
                throw new RuntimeException("saveKairosMetadata with raw http client failed ! url is " + url);
            }
        } catch (Exception e) {
            throw new RuntimeException("saveKairosMetadata  with raw http client failed ! url is " + url, e);
        }
    }

    public static String getKairosMetadata(String url) {
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Content-Type", "application/json");
        try (CloseableHttpResponse response = RAW_HTTPCLIENT.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                String metadataJSON = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                return metadataJSON;
            } else {
                throw new RuntimeException("getKairosMetadata with raw http client failed ! url is " + url);
            }
        } catch (Exception e) {
            throw new RuntimeException("getKairosMetadata  with raw http client failed ! url is " + url, e);
        }
    }

    public static void hackKairosMetricWrite(MetricEntity metric, MachineDiscovery machineDiscovery, ResourceDiscovery resourceDiscovery) {
        //trigger resource first
        resourceDiscovery.addResource(metric.getApp(), metric.getResource());

        KAIROS_EXECUTOR_SERVICE.submit(() -> {
            String appName = metric.getApp();
            String kairosAddress = "http://localhost:10101";
            KairosApplicationEntity kairosApplication = new KairosApplicationEntity();
            AppInfo appInfo = machineDiscovery.getDetailApp(appName);
            if (Objects.nonNull(appInfo)) {
                kairosApplication.setMachines(appInfo.getMachines());
            } else {
                String kairosMetadata = getKairosMetadata(kairosAddress + METADATA_SERVICE_PATH + "app/" + metric.getApp());
                kairosApplication = GSON.fromJson(kairosMetadata, KairosApplicationEntity.class);
                kairosApplication.getMachines().stream().forEach(machineInfo -> {
                    machineDiscovery.addMachine(machineInfo);
                });
                kairosApplication.getResources().stream().forEach(resource -> {
                    resourceDiscovery.addResource(appName, resource);
                });
            }
            kairosApplication.setApp(appName);
            kairosApplication.setResources(resourceDiscovery.getResources(metric.getApp()));

            String applicationJSON = GSON.toJson(kairosApplication);
            saveKairosMetadata(kairosAddress + METADATA_SERVICE_PATH + "app/" + metric.getApp(), applicationJSON);
        });
    }
}
