package com.alibaba.csp.sentinel.dashboard.datasource.entity;

import com.alibaba.csp.sentinel.dashboard.discovery.MachineInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * for kairiosDB metadata
 */
public class KairosApplicationEntity {
    private String app;
    private Set<MachineInfo> machines = new HashSet<>();
    private Set<String> resources = new HashSet<>();

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public Set<MachineInfo> getMachines() {
        return machines;
    }

    public void setMachines(Set<MachineInfo> machines) {
        this.machines = machines;
    }

    public Set<String> getResources() {
        return resources;
    }

    public void setResources(Set<String> resources) {
        this.resources = resources;
    }
}
