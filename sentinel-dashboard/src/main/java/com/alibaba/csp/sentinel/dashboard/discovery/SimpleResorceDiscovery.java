package com.alibaba.csp.sentinel.dashboard.discovery;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class SimpleResorceDiscovery implements ResourceDiscovery {
    private final ConcurrentMap<String, Set<String>> resources = new ConcurrentHashMap<>();

    @Override
    public Set<String> getResources(String app) {
        Set<String> resourcesOfApp = resources.get(app);
        if (Objects.isNull(resourcesOfApp)) {
            return Collections.emptySet();
        }
        return resourcesOfApp;
    }
}
