package com.lowdragmc.lowdraglib2.editor.resource;

public class EditorResourceEvent {
    public static class Register {
        public <T> ResourceInstance<T> getResourceInstance(Class<T> type) {
            return new ResourceInstance<>();
        }
    }
}
