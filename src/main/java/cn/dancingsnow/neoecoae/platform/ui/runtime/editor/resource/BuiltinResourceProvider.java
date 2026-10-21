package cn.dancingsnow.neoecoae.platform.ui.runtime.editor.resource;
import java.util.*;
public final class BuiltinResourceProvider<T> {
    private final String name;private final Map<String,T> resources=new LinkedHashMap<>();
    public BuiltinResourceProvider(String name,ResourceInstance<T> instance){this.name=name;}
    public String name(){return name;}
    public void addResource(String path,T value){resources.put(path,Objects.requireNonNull(value));}
    public T getResource(String path){return resources.get(path);}
}
