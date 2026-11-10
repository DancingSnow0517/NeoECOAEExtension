package cn.dancingsnow.neoecoae.mixins.client.ae2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.menu.me.crafting.CraftingStatusEntry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class CraftingStatusSynchronizationTest {
    @Test
    void unresolvedMergedRowsNeverReplaceOrCreateRenderableEntries() throws Exception {
        var screen = mock(CraftingCpuScreenMixin.class, CALLS_REAL_METHODS);
        var key = mock(AEKey.class);
        var previous = new CraftingStatusEntry(1, key, 10, 0, 0);
        Map<Object, Object> entries = new HashMap<>();
        entries.put(1L, previous);
        Operation<Object> put = args -> entries.put(args[1], args[2]);

        assertSame(previous, put(screen, entries, 1L, new CraftingStatusEntry(1, null, 9, 0, 0), put));
        assertNull(put(screen, entries, 2L, new CraftingStatusEntry(2, null, 9, 0, 0), put));
        assertEquals(Map.of(1L, previous), entries);

        var resolved = new CraftingStatusEntry(1, key, 8, 0, 0);
        assertSame(previous, put(screen, entries, 1L, resolved, put));
        assertSame(resolved, entries.get(1L));
    }

    @Test
    void allNativeStatusInsertionsPassThroughTheGuardAfterKeyResolution() throws Exception {
        var target = new ClassNode();
        try (var stream = CraftingCPUScreen.class.getResourceAsStream("CraftingCPUScreen.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(target, 0);
        }
        var update = target.methods.stream().filter(method -> method.name.equals("postUpdate"))
            .findFirst().orElseThrow();
        var hook = CraftingCpuScreenMixin.class.getDeclaredMethod("neoecoae$putResolvedStatusEntry",
            Map.class, Object.class, Object.class, Operation.class)
            .getAnnotation(com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation.class);
        assertEquals("postUpdate", hook.method()[0]);
        long puts = java.util.Arrays.stream(update.instructions.toArray())
            .filter(instruction -> instruction instanceof MethodInsnNode call
                && ("L" + call.owner + ";" + call.name + call.desc).equals(hook.at()[0].target())).count();
        assertEquals(3, puts, "existing rows, resolved deltas and newly received rows must all be checked");
    }

    @Test
    void ecoRenderingRejectsMissingKeysBeforeLookingUpExactQuantities() throws Exception {
        var renderer = new CraftingStatusTableMixin();
        var invalid = new CraftingStatusEntry(1, null, 1, 1, 1);
        for (String name : List.of("exactDescription", "exactTooltip")) {
            var callback = new CallbackInfoReturnable<List<Component>>(name, true, List.of(Component.literal("old")));
            var method = CraftingStatusTableMixin.class.getDeclaredMethod(name,
                CraftingStatusEntry.class, CallbackInfoReturnable.class);
            method.setAccessible(true);
            method.invoke(renderer, invalid, callback);
            assertTrue(callback.isCancelled());
            assertTrue(callback.getReturnValue().isEmpty());
        }
    }

    private static Object put(CraftingCpuScreenMixin screen, Map<?, ?> entries, Object serial, Object value,
            Operation<Object> original) throws Exception {
        var method = CraftingCpuScreenMixin.class.getDeclaredMethod("neoecoae$putResolvedStatusEntry",
            Map.class, Object.class, Object.class, Operation.class);
        method.setAccessible(true);
        return method.invoke(screen, entries, serial, value, original);
    }
}
