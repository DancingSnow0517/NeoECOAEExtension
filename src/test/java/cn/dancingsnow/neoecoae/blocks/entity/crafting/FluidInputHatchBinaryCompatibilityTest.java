package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LongFluidTank;
import java.lang.invoke.MethodHandles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.objectweb.asm.Opcodes.*;

class FluidInputHatchBinaryCompatibilityTest {
    private AddonHatch hatch;

    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @BeforeEach
    void setup() {
        var type = mock(BlockEntityType.class);
        when(type.isValid(any())).thenReturn(true);
        hatch = new AddonHatch(type);
    }

    @Test
    void bothReleasedFieldDescriptorsResolveToTheSameLiveBuffer() throws Throwable {
        // Emit the actual GETFIELD instructions used by already compiled addons. Recompiling Java
        // source against the current hatch would only test the current LongFluidTank descriptor.
        for (Class<?> owner : new Class<?>[] {ECOFluidInputHatchBlockEntity.class, AddonHatch.class}) {
            for (Class<?> descriptor : new Class<?>[] {FluidTank.class, LongFluidTank.class}) {
                FluidTank resolved = readField(owner, descriptor);
                assertSame(hatch.getFluidTankHandler(), resolved);
                assertEquals(1000, resolved.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
                assertEquals(1000, hatch.getStoredFluidAmount());
                assertEquals(1000, hatch.getFluidTankHandler().drain(1000, FluidAction.EXECUTE).getAmount());
                assertTrue(resolved.isEmpty());
            }
        }
    }

    @Test
    void bothDescriptorsPreserveExactStockAndNbtAcrossReloads() throws Throwable {
        hatch.networkPull = true;
        long exact = 5_000_000_001L;
        hatch.tank.add(new FluidStack(Fluids.WATER, 1), exact);
        FluidTank legacy = readField(ECOFluidInputHatchBlockEntity.class, FluidTank.class);
        assertEquals(Integer.MAX_VALUE, legacy.getFluidAmount());
        assertEquals(exact, hatch.getStoredFluidAmount());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved = new CompoundTag();
        hatch.saveAdditional(saved, registries);
        hatch.tank.setFluid(FluidStack.EMPTY);
        hatch.loadTag(saved, registries);
        assertEquals(exact, hatch.getStoredFluidAmount());
        assertSame(legacy, readField(ECOFluidInputHatchBlockEntity.class, LongFluidTank.class));
        assertEquals(1000, legacy.drain(1000, FluidAction.SIMULATE).getAmount());
        assertEquals(exact, hatch.getStoredFluidAmount());
        assertEquals(1000, legacy.drain(1000, FluidAction.EXECUTE).getAmount());
        assertEquals(exact - 1000, hatch.getStoredFluidAmount());
    }

    @Test
    void legacyHandlerKeepsTheSixteenBucketLimitWithoutThePullCard() throws Throwable {
        FluidTank legacy = readField(ECOFluidInputHatchBlockEntity.class, FluidTank.class);
        assertEquals(16_000, legacy.getCapacity());
        assertEquals(16_000, legacy.fill(new FluidStack(Fluids.WATER, 20_000), FluidAction.EXECUTE));
        assertEquals(0, legacy.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE));
        assertEquals(16_000, hatch.getStoredFluidAmount());
    }

    private FluidTank readField(Class<?> owner, Class<?> descriptor) throws Throwable {
        String name = Type.getInternalName(getClass()) + "$Reader" + owner.getSimpleName() + descriptor.getSimpleName();
        var writer = new ClassWriter(0);
        writer.visit(V21, ACC_PUBLIC, name, null, "java/lang/Object", null);
        var method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "read", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitTypeInsn(CHECKCAST, Type.getInternalName(owner));
        method.visitFieldInsn(GETFIELD, Type.getInternalName(owner), "tank", Type.getDescriptor(descriptor));
        method.visitInsn(ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        Class<?> reader = MethodHandles.lookup().defineHiddenClass(writer.toByteArray(), true).lookupClass();
        var read = MethodHandles.lookup().findStatic(reader, "read",
            java.lang.invoke.MethodType.methodType(Object.class, Object.class));
        return (FluidTank) (Object) read.invokeExact((Object) hatch);
    }

    public static class AddonHatch extends ECOFluidInputHatchBlockEntity {
        boolean networkPull;

        AddonHatch(BlockEntityType<?> type) { super(type, BlockPos.ZERO, Blocks.AIR.defaultBlockState()); }

        @Override protected IManagedGridNode createMainNode() { return mock(IManagedGridNode.class, RETURNS_SELF); }
        @Override public boolean hasNetworkPullCard() { return networkPull; }
        @Override public void setChanged() {}
        @Override public void markForUpdate() {}
    }
}
