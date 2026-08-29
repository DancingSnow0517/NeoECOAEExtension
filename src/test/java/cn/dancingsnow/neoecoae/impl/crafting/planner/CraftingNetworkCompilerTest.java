package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CraftingNetworkCompiler;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class CraftingNetworkCompilerTest {
    @Test void malformedCandidateIsLocalizedAndLaterCandidateStillCompiles() throws Exception {
        var a = PlannerTestKey.of("compiler_a"); var g = PlannerTestKey.of("compiler_g");
        IPatternDetails malformed = new BrokenPattern(g);
        var valid = PlannerFixtures.pattern("valid", g, 1, a, 1L);
        var network = new CraftingNetworkCompiler().compile(service(Map.of(g, List.of(malformed, valid))), g,
            ECOCancellation.NONE);
        assertEquals(2, network.producersOf(g).size());
        assertFalse(network.producersOf(g).get(0).fastSupported());
        assertTrue(network.producersOf(g).get(1).fastSupported());
        assertTrue(network.keys().contains(a));
    }

    @Test void substitutionAndRemainderAreClassifiedForNativeFallback() throws Exception {
        var a = PlannerTestKey.of("compiler_sub_a"); var b = PlannerTestKey.of("compiler_sub_b");
        var g1 = PlannerTestKey.of("compiler_sub_g"); var g2 = PlannerTestKey.of("compiler_rem_g");
        IPatternDetails substitution = pattern(g1, new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() {
                return new GenericStack[] { new GenericStack(a, 1), new GenericStack(b, 1) };
            }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey input, Level level) { return input.equals(a) || input.equals(b); }
            @Override public AEKey getRemainingKey(AEKey template) { return null; }
        });
        IPatternDetails remainder = pattern(g2, new PlannerFixtures.Input(a, 1, true));
        var service = service(Map.of(g1, List.of(substitution), g2, List.of(remainder)));
        var subNetwork = new CraftingNetworkCompiler().compile(service, g1, ECOCancellation.NONE);
        var remNetwork = new CraftingNetworkCompiler().compile(service, g2, ECOCancellation.NONE);
        assertEquals("UNSUPPORTED_SUBSTITUTION", subNetwork.producersOf(g1).getFirst().unsupportedReason());
        assertEquals("UNSUPPORTED_REMAINDER", remNetwork.producersOf(g2).getFirst().unsupportedReason());
    }

    private static IPatternDetails pattern(AEKey output, IPatternDetails.IInput input) {
        return new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return null; }
            @Override public IInput[] getInputs() { return new IInput[] {input}; }
            @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, 1)); }
        };
    }

    private static ICraftingService service(Map<AEKey, ? extends Collection<IPatternDetails>> patterns) {
        return (ICraftingService) Proxy.newProxyInstance(ICraftingService.class.getClassLoader(),
            new Class<?>[] {ICraftingService.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getCraftingFor" -> patterns.containsKey((AEKey) args[0])
                    ? patterns.get((AEKey) args[0]) : List.of();
                case "canEmitFor" -> false;
                case "toString" -> "PlannerCompilerTestService";
                default -> method.getReturnType() == boolean.class ? false :
                    method.getReturnType() == long.class ? 0L : method.getReturnType() == int.class ? 0 : null;
            });
    }

    private record BrokenPattern(AEKey output) implements IPatternDetails {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { throw new IllegalStateException("broken input contract"); }
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, 1)); }
    }
}
