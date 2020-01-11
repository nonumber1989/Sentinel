package com.alibaba.csp.sentinel.dashboard.datasource.entity;

import java.util.Set;

/**
 * for kairiosDB metadata
 */
public class KairosApplicationEntity {
    private String app;
    private Set<MachineEntity> machines;
    private Set<String> resources;

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public Set<MachineEntity> getMachines() {
        return machines;
    }

    public void setMachines(Set<MachineEntity> machines) {
        this.machines = machines;
    }

    public Set<String> getResources() {
        return resources;
    }

    public void setResources(Set<String> resources) {
        this.resources = resources;
    }
}
