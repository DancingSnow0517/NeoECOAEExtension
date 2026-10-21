package cn.dancingsnow.neoecoae.platform.sync;

import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NEFluidStackAccessorTest {
    @BeforeAll static void bootstrap(){PlatformTestBootstrap.initialize();}

    @Test void preservesForgeFluidIdentityAmountAndNbtWithoutSharingMutableTags(){
        var accessor=new NEFluidStackAccessor();
        var tag=new CompoundTag();tag.putString("coolant","test");
        var source=new FluidStack(Fluids.WATER,1250,tag);
        var snapshot=accessor.serialize(AccessorOp.SYNCED,source);
        source.getTag().putString("coolant","modified");
        var restored=accessor.deserialize(AccessorOp.SYNCED,snapshot);
        assertEquals(Fluids.WATER,restored.getFluid());assertEquals(1250,restored.getAmount());
        assertEquals("test",restored.getTag().getString("coolant"));
        restored.getTag().putString("coolant","receiver edit");
        assertEquals("test",accessor.deserialize(AccessorOp.SYNCED,snapshot).getTag().getString("coolant"));
    }

    @Test void emptyCoolantRemainsEmpty(){
        var accessor=new NEFluidStackAccessor();
        assertTrue(accessor.deserialize(AccessorOp.PERSISTED,accessor.serialize(AccessorOp.PERSISTED,FluidStack.EMPTY)).isEmpty());
    }

    @Test void rejectsNonCompoundPayloads(){
        assertThrows(IllegalArgumentException.class,()->new NEFluidStackAccessor().deserialize(AccessorOp.SYNCED,
            NbtTagPayload.of(StringTag.valueOf("water"))));
    }
}
