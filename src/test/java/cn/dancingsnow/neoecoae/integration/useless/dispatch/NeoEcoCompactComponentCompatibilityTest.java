package cn.dancingsnow.neoecoae.integration.useless.dispatch;

import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.ECOFluidHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.objectweb.asm.Opcodes.*;

public class NeoEcoCompactComponentCompatibilityTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    public static IManagedGridNode newMainNode() {
        return mock(IManagedGridNode.class, RETURNS_SELF);
    }

    @Test
    @SuppressWarnings("unchecked")
    void compiledShadowCoresConstructAndContributeToCurrentClusters() throws Exception {
        var computation = (ECOParallelCoreBlockEntity<NEComputationCluster>) shadow(
            "computation/ECOComputationParallelCoreBlockEntity", true);
        var crafting = (ECOParallelCoreBlockEntity<NECraftingCluster>) shadow(
            "crafting/ECOCraftingParallelCoreBlockEntity", true);
        var computationCluster = new NEComputationCluster(BlockPos.ZERO, BlockPos.ZERO);
        var craftingCluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
        computationCluster.addBlockEntity(computation);
        craftingCluster.addBlockEntity(crafting);

        assertSame(ECOTier.L9, computation.getTier());
        assertSame(ECOTier.L9, crafting.getTier());
        assertEquals(ECOTier.L9.getCPUAccelerators(), computationCluster.getCPUAccelerators());
        assertEquals(java.util.List.of(crafting), craftingCluster.getParallelCores());
    }

    @Test
    @SuppressWarnings("unchecked")
    void compiledShadowHatchesResolveOldTankOwnersAndJoinCurrentCluster() throws Exception {
        var input = (ECOFluidHatchBlockEntity<NECraftingCluster>) shadow(
            "crafting/ECOFluidInputHatchBlockEntity", false);
        var output = (ECOFluidHatchBlockEntity<NECraftingCluster>) shadow(
            "crafting/ECOFluidOutputHatchBlockEntity", false);
        var cluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
        cluster.addBlockEntity(input);
        cluster.addBlockEntity(output);

        assertSame(input, cluster.getInputHatch());
        assertSame(output, cluster.getOutputHatch());
        assertTrue(input.isInput());
        assertFalse(output.isInput());
        for (var hatch : java.util.List.of(input, output)) {
            var tank = (FluidTank) hatch.getClass().getMethod("legacyTank").invoke(hatch);
            assertSame(hatch.tank, tank);
            assertEquals(16_000, tank.getCapacity());
        }
    }

    // Emit the old invokespecial/GETFIELD owners used by Useless 2.4.5.11, rather than recompiling
    // those calls against the new superclass. Only AE2's grid-node creation is replaced with a mock.
    private static NEBlockEntity<?, ?> shadow(String oldName, boolean core) throws Exception {
        String owner = "cn/dancingsnow/neoecoae/blocks/entity/" + oldName;
        String name = "fixture/" + oldName.substring(oldName.lastIndexOf('/') + 1) + "Shadow";
        String args = "Lnet/minecraft/world/level/block/entity/BlockEntityType;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;";
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V21, ACC_PUBLIC, name, null, owner, null);
        var constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "(" + args + ")V", null, null);
        constructor.visitCode();
        for (int index = 0; index < 4; index++) constructor.visitVarInsn(ALOAD, index);
        if (core) constructor.visitFieldInsn(GETSTATIC, Type.getInternalName(ECOTier.class), "L9",
            Type.getDescriptor(ECOTier.class));
        constructor.visitMethodInsn(INVOKESPECIAL, owner, "<init>", "(" + args
            + (core ? "Lcn/dancingsnow/neoecoae/api/IECOTier;" : "") + ")V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        var node = writer.visitMethod(ACC_PROTECTED, "createMainNode",
            "()Lappeng/api/networking/IManagedGridNode;", null, null);
        node.visitCode();
        node.visitMethodInsn(INVOKESTATIC, Type.getInternalName(NeoEcoCompactComponentCompatibilityTest.class),
            "newMainNode", "()Lappeng/api/networking/IManagedGridNode;", false);
        node.visitInsn(ARETURN);
        node.visitMaxs(0, 0);
        node.visitEnd();
        if (!core) {
            var tank = writer.visitMethod(ACC_PUBLIC, "legacyTank",
                "()Lnet/neoforged/neoforge/fluids/capability/templates/FluidTank;", null, null);
            tank.visitCode();
            tank.visitVarInsn(ALOAD, 0);
            tank.visitFieldInsn(GETFIELD, owner, "tank",
                "Lnet/neoforged/neoforge/fluids/capability/templates/FluidTank;");
            tank.visitInsn(ARETURN);
            tank.visitMaxs(0, 0);
            tank.visitEnd();
        }
        writer.visitEnd();
        var fixture = new ClassLoader(NeoEcoCompactComponentCompatibilityTest.class.getClassLoader()) {
            Class<?> loadFixture() {
                byte[] bytes = writer.toByteArray();
                return defineClass(name.replace('/', '.'), bytes, 0, bytes.length);
            }
        }.loadFixture();
        var type = mock(BlockEntityType.class);
        when(type.isValid(any())).thenReturn(true);
        return (NEBlockEntity<?, ?>) fixture.getConstructor(BlockEntityType.class, BlockPos.class, BlockState.class)
            .newInstance(type, BlockPos.ZERO, Blocks.AIR.defaultBlockState());
    }
}
