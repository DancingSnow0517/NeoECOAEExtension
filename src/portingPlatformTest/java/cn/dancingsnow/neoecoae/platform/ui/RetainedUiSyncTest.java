package cn.dancingsnow.neoecoae.platform.ui;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.ValueWire;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RetainedUiSyncTest {
    @BeforeAll static void bootstrap(){PlatformTestBootstrap.initialize();}
    @Test void initialSnapshotCarriesRealNbtAndDoesNotEchoIntoTheServerSetter(){
        var data=new CompoundTag();data.putString("exact","1000000000000000000000000000017");
        var server=new BindableValue<Tag>(new CompoundTag());server.bind(DataBindingBuilder.tagS2C(()->data).build());
        var client=new BindableValue<Tag>(new CompoundTag());client.bind(DataBindingBuilder.tagS2C(CompoundTag::new).build());
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{server.writeInitialData(buffer);client.readInitialData(buffer);assertEquals(data,client.getValue());assertNotSame(data,client.getValue());assertFalse(buffer.isReadable());}
        finally{buffer.release();}
    }
    @Test void activeMenuActionRunsItsSetterOnceAndClosedMenuActionsAreIgnored(){
        class Request extends BindableValue<Integer>{boolean open=true;Request(){super(0);}@Override public boolean activeMenu(){return open;}}
        var request=new Request();AtomicInteger changes=new AtomicInteger();request.bind(DataBindingBuilder.intValC2S(changes::addAndGet).build());
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{buffer.writeVarInt(0);ValueWire.write(buffer,Integer.class,7);request.handleClientAction(700,buffer);assertEquals(7,changes.get());assertEquals(7,request.getValue());
            buffer.clear();buffer.writeVarInt(0);ValueWire.write(buffer,Integer.class,9);request.open=false;request.handleClientAction(700,buffer);assertEquals(7,changes.get());}
        finally{buffer.release();}
    }
    @Test void nullAndStructuredValuesRoundTripWithoutAliasing(){
        var tag=new CompoundTag();tag.putInt("size",42);var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{ValueWire.write(buffer,Tag.class,tag);ValueWire.write(buffer,String.class,null);var decoded=(CompoundTag)ValueWire.read(buffer,Tag.class);
            assertEquals(tag,decoded);assertNotSame(tag,decoded);assertNull(ValueWire.read(buffer,String.class));assertFalse(buffer.isReadable());}
        finally{buffer.release();}
    }
}
