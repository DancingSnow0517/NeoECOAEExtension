package cn.dancingsnow.neoecoae.api.me.pattern;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.AE2PatternIntrospection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ECOExecutionOptimizationRegistryTest {
    private final List<ResourceLocation> registered = new ArrayList<>();
    private final IPatternDetails pattern = mock(IPatternDetails.class);

    @AfterEach void cleanup() {
        registered.forEach(ECOExecutionOptimizationRegistry::unregister);
    }

    @Test void ordinaryPatternHasNoImplicitVirtualExecutionPermission() {
        assertFalse(ECOExecutionOptimizationRegistry.resolve(pattern).verifiedAssemblerFastPath());
    }

    @Test void higherPriorityCanDenyBroaderAuthorization() {
        register("broad", -100, ECOExecutionOptimization.VERIFIED_ASSEMBLER);
        register("specific", 100, ECOExecutionOptimization.NONE);
        assertFalse(ECOExecutionOptimizationRegistry.resolve(pattern).verifiedAssemblerFastPath());
    }

    @Test void equalPriorityUsesIdRatherThanRegistrationOrder() {
        register("z_allowed", 100, ECOExecutionOptimization.VERIFIED_ASSEMBLER);
        register("a_denied", 100, ECOExecutionOptimization.NONE);
        assertFalse(ECOExecutionOptimizationRegistry.resolve(pattern).verifiedAssemblerFastPath());
    }

    @Test void failedAdapterDoesNotFallThroughToBroaderAuthorization() {
        register("fallback", -100, ECOExecutionOptimization.VERIFIED_ASSEMBLER);
        ResourceLocation id = id("failing");
        ECOExecutionOptimizationRegistry.register(id, 100, new ECOExecutionOptimizationAdapter() {
            @Override public boolean supports(IPatternDetails details) { return true; }
            @Override public ECOExecutionOptimization describe(IPatternDetails details) {
                throw new IllegalStateException("Broken special adapter");
            }
        });
        registered.add(id);
        assertFalse(ECOExecutionOptimizationRegistry.resolve(pattern).verifiedAssemblerFastPath());
    }

    @Test void registrationRemovalInvalidatesOldContractAndDuplicateIdsAreRejected() {
        long generation = AE2PatternIntrospection.reloadGeneration();
        long revision = ECOExecutionOptimizationRegistry.revision();
        ResourceLocation id = register("lifecycle", 0, ECOExecutionOptimization.VERIFIED_ASSEMBLER);
        assertTrue(ECOExecutionOptimizationRegistry.resolve(pattern).verifiedAssemblerFastPath());
        assertTrue(AE2PatternIntrospection.reloadGeneration() > generation);
        assertTrue(ECOExecutionOptimizationRegistry.revision() > revision);

        long registeredGeneration = AE2PatternIntrospection.reloadGeneration();
        assertThrows(IllegalArgumentException.class, () -> register("lifecycle", 0, ECOExecutionOptimization.NONE));
        assertEquals(registeredGeneration, AE2PatternIntrospection.reloadGeneration());
        assertTrue(ECOExecutionOptimizationRegistry.unregister(id));
        assertTrue(AE2PatternIntrospection.reloadGeneration() > registeredGeneration);
        assertFalse(ECOExecutionOptimizationRegistry.resolve(pattern).verifiedAssemblerFastPath());
    }

    private ResourceLocation register(String path, int priority, ECOExecutionOptimization capability) {
        ResourceLocation id = id(path);
        ECOExecutionOptimizationRegistry.register(id, priority, new ECOExecutionOptimizationAdapter() {
            @Override public boolean supports(IPatternDetails details) { return details == pattern; }
            @Override public ECOExecutionOptimization describe(IPatternDetails details) { return capability; }
        });
        registered.add(id);
        return id;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("eco_execution_test", path);
    }
}
