package com.alibaba.csp.sentinel.dashboard.util;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.StandardHttpRequestRetryHandler;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;

/**
 * TODO 别玩了 好不如直接耦合kairosDB
 * 封装毛线
 */
public class HttpClientUtils {
    private static HttpClientBuilder HTTPCLIENT_BUILDER = HttpClientBuilder.create().setRetryHandler(new StandardHttpRequestRetryHandler());
    private static CloseableHttpClient RAW_HTTPCLIENT = HTTPCLIENT_BUILDER.build();


    public static String doHttpPost(String url, String bodyJsonParams, Map<String, String> headers) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(bodyJsonParams, "UTF-8"));

        if (headers != null && headers.keySet().isEmpty()) {
            Set<String> keySet = headers.keySet();
            Iterator<String> iterator = keySet.iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                String value = headers.get(key);
                httpPost.addHeader(key, value);
            }
        }
        return execute(httpPost);
    }

    public static String doHttpGet(String url, Map<String, String> params, Map<String, String> headers) {
        StringBuilder paramsBuilder = new StringBuilder(url);
        if (params != null && params.keySet().isEmpty()) {
            if (url.indexOf("?") == -1) {
                paramsBuilder.append("?");
            }
            List<NameValuePair> list = new ArrayList<>();
            Set<String> keySet = headers.keySet();
            Iterator<String> iterator = keySet.iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                String value = headers.get(key);
                list.add(new BasicNameValuePair(key, value));
            }
            try {
                String paramsString = EntityUtils.toString(new UrlEncodedFormEntity(list, Charset.defaultCharset()), Charset.defaultCharset());
                paramsBuilder.append(paramsString);
            } catch (Exception e) {
                new RuntimeException("compose request parameter failed !", e);
            }
        }
        HttpGet httpGet = new HttpGet(paramsBuilder.toString());
        if (headers != null && headers.keySet().isEmpty()) {
            Set<String> keySet = headers.keySet();
            Iterator<String> iterator = keySet.iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                String value = headers.get(key);
                httpGet.addHeader(key, value);
            }

        }
        return execute(httpGet);
    }

    private static String execute(HttpUriRequest httpUriRequest) {
        try {
            CloseableHttpResponse response = RAW_HTTPCLIENT.execute(httpUriRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                try (BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent()))) {
                    String result = "";
                    String tmp = null;
                    while ((tmp = bufferedReader.readLine()) != null) {
                        result += tmp;
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            new RuntimeException("request kairosDB with raw http client failed !", e);
        }
        return null;
    }
}
