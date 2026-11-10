package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.menu.me.common.MEStorageMenu;
import cn.dancingsnow.neoecoae.mixins.terminalbigamount.MEStorageMenuExactAmountMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Set;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class MEStorageMenuListingMixinTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void realAe2UpdateUsesTheThreeExpectedCounterCalls() throws Exception {
        var node = new ClassNode();
        try (var stream = MEStorageMenu.class.getResourceAsStream("MEStorageMenu.class")) {
            new ClassReader(stream).accept(node, 0);
        }
        assertTrue(node.fields.stream().anyMatch(field -> field.name.equals("previousAvailableStacks")
                && field.desc.equals("Lappeng/api/stacks/KeyCounter;")));
        var update = node.methods.stream().filter(method -> method.name.equals("broadcastChanges") && method.desc.equals("()V"))
                .findFirst().orElseThrow();
        for (String expected : new String[] {"removeAll(Lappeng/api/stacks/KeyCounter;)V", "removeZeros()V", "keySet()Ljava/util/Set;"}) {
            long count = java.util.stream.StreamSupport.stream(update.instructions.spliterator(), false)
                    .filter(instruction -> instruction instanceof MethodInsnNode call
                            && call.owner.equals("appeng/api/stacks/KeyCounter") && (call.name + call.desc).equals(expected)).count();
            assertEquals(1, count, expected);
        }
        var mixin = new ClassNode();
        try (var stream = MEStorageMenuExactAmountMixin.class.getResourceAsStream("MEStorageMenuExactAmountMixin.class")) {
            new ClassReader(stream).accept(mixin, 0);
        }
        Set<String> grouped = new java.util.HashSet<>();
        Set<String> wrapped = new java.util.HashSet<>();
        for (var method : mixin.methods) {
            for (var annotations : java.util.Arrays.asList(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (annotations == null) continue;
                for (var annotation : annotations) {
                    if (annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;")) {
                        wrapped.add(method.name);
                        assertEquals(java.util.List.of("broadcastChanges()V", "m_38946_()V"),
                                annotation.values.get(annotation.values.indexOf("method") + 1),
                                method.name + " must target both development and production AE2 names");
                    }
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Group;")) continue;
                    grouped.add(method.name);
                    assertEquals("neoecoae$inventoryChanges", annotation.values.get(annotation.values.indexOf("name") + 1));
                    assertEquals(3, annotation.values.get(annotation.values.indexOf("min") + 1));
                    assertEquals(3, annotation.values.get(annotation.values.indexOf("max") + 1));
                }
            }
        }
        assertEquals(Set.of("neoecoae$diffWithoutMutatingSnapshot", "neoecoae$skipSnapshotZeroRemoval",
            "neoecoae$reuseChangedKeys"), grouped, "all three replacements must apply together");
        assertEquals(Set.of("neoecoae$collectExactAmounts", "neoecoae$diffWithoutMutatingSnapshot",
                "neoecoae$skipSnapshotZeroRemoval", "neoecoae$reuseChangedKeys"), wrapped);
    }

    @Test void updateCollectsChangesWithoutReplacingOrMutatingTheSharedBaseline() throws Exception {
        var mixin = new MEStorageMenuExactAmountMixin() {};
        var key = AEItemKey.of(Items.STONE);
        var removed = AEItemKey.of(Items.DIRT);
        var previous = new KeyCounter();
        previous.set(key, 10);
        previous.set(removed, 3);
        var current = new KeyCounter();
        current.set(key, 12);
        baseline(mixin, previous);
        diff(mixin, previous, current, arguments -> fail("shared inventory must never be subtracted in place"));
        assertSame(previous, baseline(mixin));
        assertEquals(10, previous.get(key));
        assertEquals(3, previous.get(removed));
        assertEquals(12, current.get(key));
        assertEquals(Set.of(key, removed), keys(mixin, previous, arguments -> fail("reuse already collected keys")));
        removeZeros(mixin, previous, arguments -> fail("published inventories must stay read-only"));
        baseline(mixin, current); // AE2 assigns the freshly listed inventory at the end of broadcastChanges.
        diff(mixin, current, current, arguments -> fail("same-tick repeat still uses an isolated difference"));
        assertSame(current, baseline(mixin));
        assertTrue(keys(mixin, current, arguments -> fail("same-tick inventory is unchanged")).isEmpty());
        assertEquals(12, current.get(key));
    }

    @Test void unrelatedCountersKeepTheirOriginalOperations() throws Exception {
        var mixin = new MEStorageMenuExactAmountMixin() {};
        baseline(mixin, new KeyCounter());
        var unrelated = new KeyCounter();
        var current = new KeyCounter();
        AEKey key = AEItemKey.of(Items.STONE);
        unrelated.set(key, 2);
        current.set(key, 2);
        int[] calls = {0};
        diff(mixin, unrelated, current, arguments -> { calls[0]++; unrelated.removeAll(current); return null; });
        removeZeros(mixin, unrelated, arguments -> { calls[0]++; unrelated.removeZeros(); return null; });
        assertTrue(keys(mixin, unrelated, arguments -> { calls[0]++; return unrelated.keySet(); }).isEmpty());
        assertEquals(3, calls[0]);
    }

    private static void baseline(MEStorageMenuExactAmountMixin menu, KeyCounter value) throws Exception {
        var field = MEStorageMenuExactAmountMixin.class.getDeclaredField("previousAvailableStacks");
        field.setAccessible(true);
        field.set(menu, value);
    }

    private static KeyCounter baseline(MEStorageMenuExactAmountMixin menu) throws Exception {
        var field = MEStorageMenuExactAmountMixin.class.getDeclaredField("previousAvailableStacks");
        field.setAccessible(true);
        return (KeyCounter) field.get(menu);
    }

    private static void diff(MEStorageMenuExactAmountMixin menu, KeyCounter previous, KeyCounter current, Operation<Void> original)
            throws Exception {
        var method = MEStorageMenuExactAmountMixin.class.getDeclaredMethod("neoecoae$diffWithoutMutatingSnapshot", KeyCounter.class, KeyCounter.class, Operation.class);
        method.setAccessible(true);
        method.invoke(menu, previous, current, original);
    }

    private static void removeZeros(MEStorageMenuExactAmountMixin menu, KeyCounter counter, Operation<Void> original) throws Exception {
        var method = MEStorageMenuExactAmountMixin.class.getDeclaredMethod("neoecoae$skipSnapshotZeroRemoval", KeyCounter.class, Operation.class);
        method.setAccessible(true);
        method.invoke(menu, counter, original);
    }

    @SuppressWarnings("unchecked")
    private static Set<AEKey> keys(MEStorageMenuExactAmountMixin menu, KeyCounter counter, Operation<Set<AEKey>> original) throws Exception {
        var method = MEStorageMenuExactAmountMixin.class.getDeclaredMethod("neoecoae$reuseChangedKeys", KeyCounter.class, Operation.class);
        method.setAccessible(true);
        return (Set<AEKey>) method.invoke(menu, counter, original);
    }
}
