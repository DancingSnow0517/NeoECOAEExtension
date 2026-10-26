package cn.dancingsnow.neoecoae.compat.processing;

import static cn.dancingsnow.neoecoae.compat.processing.ProcessingTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.PatternProviderProcessingTargetMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class PatternProviderProcessingTargetTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void inventorySideIsCapturedBeforeAnImmediateFlushClearsIt() throws Exception {
        var mixin = mixin();
        set(mixin, "sendDirection", Direction.EAST);
        Operation<Boolean> flush = args -> { set(mixin, "sendDirection", null); return false; };
        assertEquals(false, invoke(mixin, "neoecoae$recordInventory", mock(PatternProviderLogic.class), flush));
        assertEquals(new BlockPos(1, 0, 0), mixin.neoecoae$getProcessingTarget().position());
    }

    @Test void rejectedCraftingMachineNeverBecomesATargetAndAcceptedSideIsReversed() throws Exception {
        var mixin = mixin();
        Operation<Boolean> rejected = args -> false, accepted = args -> true;
        var machine = mock(ICraftingMachine.class);
        var pattern = mock(IPatternDetails.class);
        assertEquals(false, invoke(mixin, "neoecoae$recordMachine", machine, pattern, new KeyCounter[0], Direction.EAST, rejected));
        assertNull(mixin.neoecoae$getProcessingTarget());
        assertEquals(true, invoke(mixin, "neoecoae$recordMachine", machine, pattern, new KeyCounter[0], Direction.EAST, accepted));
        assertEquals(new BlockPos(-1, 0, 0), mixin.neoecoae$getProcessingTarget().position());
        invoke(mixin, "neoecoae$resetTarget", pattern, new KeyCounter[0], null);
        assertNull(mixin.neoecoae$getProcessingTarget());
    }

    @Test void bothMandatoryMixinHooksExistInTheActualAe2DependencyBytecode() throws Exception {
        var node = new ClassNode();
        try (var input = PatternProviderLogic.class.getResourceAsStream("PatternProviderLogic.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        var push = node.methods.stream().filter(m -> m.name.equals("pushPattern")).findFirst().orElseThrow();
        var calls = Arrays.stream(push.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
            .map(MethodInsnNode.class::cast).toList();
        assertEquals(1, calls.stream().filter(m -> m.owner.equals("appeng/helpers/patternprovider/PatternProviderLogic")
            && m.name.equals("sendStacksOut") && m.desc.equals("()Z")).count());
        assertEquals(1, calls.stream().filter(m -> m.owner.equals("appeng/api/implementations/blockentities/ICraftingMachine")
            && m.name.equals("pushPattern") && m.desc.equals("(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;Lnet/minecraft/core/Direction;)Z")).count());
    }

    private static PatternProviderProcessingTargetMixin mixin() {
        var mixin = new PatternProviderProcessingTargetMixin() {};
        var host = mock(PatternProviderLogicHost.class);
        var be = machine(BlockEntity.class);
        when(host.getBlockEntity()).thenReturn(be);
        set(mixin, "host", host);
        return mixin;
    }
    private static Object invoke(Object mixin, String name, Object... args) throws Exception {
        var method = Arrays.stream(PatternProviderProcessingTargetMixin.class.getDeclaredMethods())
            .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        method.setAccessible(true);
        return method.invoke(mixin, args);
    }
}
