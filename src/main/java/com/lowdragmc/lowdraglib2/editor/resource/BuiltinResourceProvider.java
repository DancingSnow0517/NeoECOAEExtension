package com.lowdragmc.lowdraglib2.editor.resource;

import java.util.HashMap;
import java.util.Map;

public class BuiltinResourceProvider<T> {
    private final String id;
    private final ResourceInstance<T> instance;
    private final Map<String, T> resources = new HashMap<>();

    public BuiltinResourceProvider(String id, ResourceInstance<T> instance) {
        this.id = id;
        this.instance = instance;
    }

    public void addResource(String name, T resource) {
        resources.put(name, resource);
    }

    public String id() {
        return id;
    }

    public ResourceInstance<T> instance() {
        return instance;
    }
}
