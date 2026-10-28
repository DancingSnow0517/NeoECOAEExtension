package cn.dancingsnow.neoecoae.util;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.style.StylesheetManager;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.ValueWire;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;

/** Test fixtures inspect actual retained runtime state without recreating the removed upstream API. */
public final class UiTestSupport {
    private UiTestSupport() {}
    private static Field field(Class<?> type, String name) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try { Field result=cursor.getDeclaredField(name);result.setAccessible(true);return result; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new AssertionError("Missing runtime field " + type.getName() + "." + name);
    }
    public static void set(Object target, String name, Object value) {
        try { field(target.getClass(),name).set(target,value); }
        catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }
    public static Object get(Object target, String name) {
        try { return field(target.getClass(),name).get(target); }
        catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }
    public static int color(Object texture) { return (int)get(texture,"color"); }
    public static float translationY(UIElement element) { return (float)get(get(element,"transform"),"y"); }
    public static ModularUI createUI(UI ui) {
        var player=org.mockito.Mockito.mock(net.minecraft.world.entity.player.Player.class);
        var level=org.mockito.Mockito.mock(net.minecraft.world.level.Level.class);
        org.mockito.Mockito.when(player.level()).thenReturn(level);
        set(level,"isClientSide",true);
        return new ModularUI(ui,player);
    }
    /** Supplies the valid-menu boundary for isolated vanilla-slot tests without Forge's menu mixins. */
    public static void bindActiveMenu(UIElement root, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.inventory.AbstractContainerMenu menu) {
        var ui = org.mockito.Mockito.mock(ModularUI.class);
        set(ui,"player",player);
        org.mockito.Mockito.when(ui.getMenu()).thenReturn(menu);
        org.mockito.Mockito.when(ui.hasValidHolder()).thenReturn(true);
        if (org.mockito.Mockito.mockingDetails(menu).isMock())
            org.mockito.Mockito.when(menu.stillValid(player)).thenReturn(true);
        root.attachUI(ui);
    }
    public static void init(ModularUI ui,int width,int height) {
        var root=ui.getRootElement();root.arrange();
        root.setSelfPosition(new com.lowdragmc.lowdraglib.utils.Position((width-root.getSizeWidth())/2,(height-root.getSizeHeight())/2));
        root.arrange();
    }
    public static void restyle(UIElement element) {
        var root=element;while(root.getParentElement()!=null)root=root.getParentElement();
        if(root.getModularUI()!=null)root.getModularUI().restyle();
        else StylesheetManager.INSTANCE.getStylesheetSafe(new net.minecraft.resources.ResourceLocation("neoecoae","lss/eco.lss")).apply(root);
    }
    @SuppressWarnings("unchecked") public static Consumer<UIEvent> serverAction(UIElement element) {
        return element instanceof Button ? (Consumer<UIEvent>)get(element,"serverClick") : null;
    }
    public static void serverClick(UIElement element,UIEvent event) { serverAction(element).accept(event); }
    @SuppressWarnings("unchecked") public static Rpc rpc(RPCEmitter emitter) {
        return new Rpc(((List<RPCEventBuilder.Event>)get(emitter.owner(),"rpcEvents")).get(emitter.index()));
    }
    public record Rpc(RPCEventBuilder.Event definition) {
        public void writeParametersToBuffer(FriendlyByteBuf buffer,Object[] values) {
            for(int i=0;i<values.length;i++)ValueWire.write(buffer,definition.parameters()[i],values[i]);
        }
        public Object[] readParametersFromBuffer(FriendlyByteBuf buffer) {
            var result=new Object[definition.parameters().length];
            for(int i=0;i<result.length;i++)result[i]=ValueWire.read(buffer,definition.parameters()[i]);
            return result;
        }
        public java.util.function.Function<Object[],Object> executor() { return definition.handler(); }
    }
}
