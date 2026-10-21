package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import java.util.function.Consumer;
public record RPCEmitter(UIElement owner,int index){
    public boolean send(Object... values){return owner.sendRpc(index,null,values);}
    public <T> boolean send(Consumer<T> callback,Object... values){return owner.sendRpc(index,value->callback.accept((T)value),values);}
}
