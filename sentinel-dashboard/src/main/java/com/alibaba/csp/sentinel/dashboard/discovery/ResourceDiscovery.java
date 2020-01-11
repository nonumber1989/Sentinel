package com.alibaba.csp.sentinel.dashboard.discovery;

import java.util.Set;

public interface ResourceDiscovery {
    Set<String> getResources(String app);
}
