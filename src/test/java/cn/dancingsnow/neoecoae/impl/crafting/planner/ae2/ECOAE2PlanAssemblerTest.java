package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOAE2PlanAssemblerTest {
    @Test
    void missingSimulationRetainsCraftChainSummary() {
        TestKey source = new TestKey("source");
        TestKey intermediate = new TestKey("intermediate");
        TestKey target = new TestKey("target");
        IPatternDetails sourceToIntermediate = pattern("source_to_intermediate");
        IPatternDetails intermediateToTarget = pattern("intermediate_to_target");
        ECOAE2PatternVariant first = new ECOAE2PatternVariant(sourceToIntermediate, 0, List.of());
        ECOAE2PatternVariant second = new ECOAE2PatternVariant(intermediateToTarget, 0, List.of());
        var problem = new ECOPlanningProblem<AEKey, ECOAE2PatternVariant>(
            List.of(
                new ECOPlanningOperation<>(first, Map.of(source, 1L), Map.of(intermediate, 1L)),
                new ECOPlanningOperation<>(second, Map.of(intermediate, 1L), Map.of(target, 1L))
            ),
            Map.of(),
            Map.of(target, 1L)
        );
        var snapshot = new ECOAE2PlanningSnapshot(
            problem, target, 1L, false, Map.of(), false, false
        );
        Map<ECOAE2PatternVariant, Long> executions = new LinkedHashMap<>();
        executions.put(first, 1L);
        executions.put(second, 1L);
        var candidate = new ECOPlanCandidate<>(executions, 0L, 0L, 1L, 0L);

        var plan = ECOAE2PlanAssembler.missingSimulationPlan(
            snapshot, candidate, Map.of(source, 1L), 64L
        );

        assertTrue(plan.simulation());
        assertEquals(1L, plan.missingItems().get(source));
        assertEquals(Map.of(sourceToIntermediate, 1L, intermediateToTarget, 1L), plan.patternTimes());
    }

    private static IPatternDetails pattern(String id) {
        return (IPatternDetails) Proxy.newProxyInstance(
            ECOAE2PlanAssemblerTest.class.getClassLoader(),
            new Class<?>[] { IPatternDetails.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "test:" + id;
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class TestKey extends AEKey {
        private final String id;

        private TestKey(String id) {
            this.id = id;
        }

        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag(HolderLookup.Provider registries) {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("test", id);
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean hasComponents() {
            return false;
        }
    }
}
