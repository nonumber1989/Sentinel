package com.alibaba.csp.sentinel.dashboard.discovery;

import java.util.Set;

public interface ResourceDiscovery {

    Long addResource(String app, String resource);

    Set<String> getResources(String app);

}
