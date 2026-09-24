package cn.dancingsnow.neoecoae.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ECOPlanningResultRegistryTest {
    private static final AEKey OUTPUT = new TestKey();

    @AfterEach
    void clearRegistry() {
        ECOPlanningResultRegistry.clear();
    }

    @Test
    void lruTracksReplacementAndEvictionWithoutLosingRecentlyReadPlans() {
        CraftingPlan[] plans = new CraftingPlan[4096];
        ECOPlanningResult first = null;
        UUID replacementId = null;
        for (int i = 0; i < plans.length; i++) {
            plans[i] = plan(i + 1L);
            UUID planningId = UUID.randomUUID();
            ECOPlanningResult result = result(plans[i], planningId);
            ECOPlanningResultRegistry.register(plans[i], result);
            if (i == 0) first = result;
            if (i == 1) replacementId = planningId;
        }
        assertSame(first, ECOPlanningResultRegistry.find(plans[0]));
        ECOPlanningResult replacement = result(plans[1], replacementId);
        ECOPlanningResultRegistry.register(plans[1], replacement);
        assertEquals(4096, ECOPlanningResultRegistry.registeredMetadataCount());

        CraftingPlan extra = plan(4097L);
        ECOPlanningResultRegistry.register(extra, result(extra, UUID.randomUUID()));
        assertEquals(4096, ECOPlanningResultRegistry.registeredMetadataCount());
        assertNull(ECOPlanningResultRegistry.find(plans[2]));
        assertSame(first, ECOPlanningResultRegistry.find(plans[0]));
        assertSame(replacement, ECOPlanningResultRegistry.find(plans[1]));
    }

    @Test
    void differentPlanningIdsForOneSignatureRemainAmbiguous() {
        CraftingPlan plan = plan(1L);
        ECOPlanningResultRegistry.register(plan, result(plan, UUID.randomUUID()));
        ECOPlanningResultRegistry.register(plan, result(plan, UUID.randomUUID()));
        assertEquals(2, ECOPlanningResultRegistry.registeredMetadataCount());
        assertNull(ECOPlanningResultRegistry.find(plan));
    }

    @Test
    void expiredEntriesAndClearUpdateTheCachedCount() throws Exception {
        CraftingPlan plan = plan(1L);
        ECOPlanningResultRegistry.register(plan, result(plan, UUID.randomUUID()));
        Method expire = ECOPlanningResultRegistry.class.getDeclaredMethod("removeExpired", long.class);
        expire.setAccessible(true);
        expire.invoke(null, System.nanoTime() + Duration.ofMinutes(11).toNanos());
        assertEquals(0, ECOPlanningResultRegistry.registeredMetadataCount());
        assertNull(ECOPlanningResultRegistry.find(plan));

        ECOPlanningResultRegistry.register(plan, result(plan, UUID.randomUUID()));
        ECOPlanningResultRegistry.clear();
        assertEquals(0, ECOPlanningResultRegistry.registeredMetadataCount());
    }

    private static CraftingPlan plan(long amount) {
        return new CraftingPlan(
                new GenericStack(OUTPUT, amount), 0L, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
    }

    private static ECOPlanningResult result(CraftingPlan plan, UUID planningId) {
        var blockedCycle = new ComponentPlanningResult(
                0, ComponentPlanningResult.Type.CYCLIC, ComponentPlanningResult.Status.UNRESOLVED,
                Map.of(), null, "test");
        return new ECOPlanningResult(
                PlanningStatus.SUCCESS, plan, new ECOPlanTrace(), List.of(), List.of(blockedCycle),
                List.of(), 0L, planningId);
    }

    private static final class TestKey extends AEKey {
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "registry");
        private static final AEKeyType TYPE = new AEKeyType(ID, TestKey.class, Component.empty()) {
            @Override public int getAmountPerByte() { return 1; }
            @Override public AEKey readFromPacket(FriendlyByteBuf buffer) { throw new UnsupportedOperationException(); }
            @Override public AEKey loadKeyFromTag(CompoundTag tag) { throw new UnsupportedOperationException(); }
        };

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() { return new CompoundTag(); }
        @Override public Object getPrimaryKey() { return this; }
        @Override public ResourceLocation getId() { return ID; }
        @Override public void writeToPacket(FriendlyByteBuf buffer) {}
        @Override protected Component computeDisplayName() { return Component.literal("registry"); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }
}
