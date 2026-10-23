package cn.dancingsnow.neoecoae.platform.ui.runtime.editor.resource;
import java.util.*;
public final class ResourceInstance<T> {
    private final Map<String,BuiltinResourceProvider<T>> providers=new LinkedHashMap<>();
    public void addBuiltinProvider(BuiltinResourceProvider<T> provider){providers.put(provider.name(),provider);}
    public BuiltinResourceProvider<T> getBuiltinProvider(String name){return providers.get(name);}
    public T getResource(BuiltinPath path){int split=path.path().indexOf(':');if(split<1)return null;
        var provider=providers.get(path.path().substring(0,split));return provider==null?null:provider.getResource(path.path().substring(split+1));}
}
