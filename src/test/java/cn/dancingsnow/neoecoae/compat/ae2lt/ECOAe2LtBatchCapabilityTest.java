package cn.dancingsnow.neoecoae.compat.ae2lt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOAe2LtBatchCapabilityTest {
    private static final String LIGHTNING = "com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider";
    private static final String TIANSHU = "com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer";

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void absentLoaderStateDisablesTheOptionalAdapter() {
        assertNull(ECOAe2LtBatchCapability.open(new Object()));
    }

    @Test
    void actualRequestRecordsPreserveInputsLimitsAndNonceForBothApis() throws Exception {
        for (String name : List.of(LIGHTNING, TIANSHU)) {
            Class<?> api = optionalApi(name);
            var requests = new ArrayList<Object>();
            Object provider = Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (p, method, args) -> {
                Object request = args[0];
                requests.add(request);
                assertEquals(api.getField("API_VERSION").get(null), field(request, "apiVersion"));
                assertEquals(api.getField("CAPABILITY_ID").get(null), field(request, "targetCapabilityId"));
                long offered = (long) field(request, "requestedAmount");
                return method.getName().equals("inspect")
                        ? record(method.getReturnType(), Map.of("capabilityVersion", 1,
                                "capabilityId", api.getField("CAPABILITY_ID").get(null),
                                "acceptedAmount", offered, "maxSafeBatch", Long.MAX_VALUE, "multiplier", 2))
                        : record(method.getReturnType(), Map.of("acceptedAmount", 3L,
                                "unacceptedAmount", offered - 3L, "resultSnapshot", List.of(), "retryable", false));
            });
            try (var loader = mockStatic(ModList.class)) {
                ModList mods = mock(ModList.class);
                loader.when(ModList::get).thenReturn(mods);
                when(mods.isLoaded("ae2lt")).thenReturn(true);
                var session = ECOAe2LtBatchCapability.open(provider);
                assertNotNull(session);
                var pattern = mock(IPatternDetails.class);
                var definition = AEItemKey.of(Items.PAPER);
                when(pattern.getDefinition()).thenReturn(definition);
                var input = new KeyCounter();
                input.add(AEItemKey.of(Items.IRON_INGOT), 2L);
                var inputs = new KeyCounter[]{input, new KeyCounter()};
                assertEquals(100L, session.inspect(pattern, inputs, 100L));
                assertTrue(session.unbounded());
                // CPU resource limits may reduce the offer after inspection.
                assertEquals(7L, session.submit(pattern, inputs, 10L));
                assertEquals(100L, field(requests.get(0), "requestedAmount"));
                assertEquals(10L, field(requests.get(1), "requestedAmount"));
                assertEquals(definition, field(requests.get(1), "processingId"));
                assertEquals(List.of(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2L)), List.of()),
                        field(requests.get(1), "inputsPerCraft"));
                assertEquals(field(requests.get(0), "nonce"), field(requests.get(1), "nonce"));
                var nextSession = ECOAe2LtBatchCapability.open(provider);
                assertNotNull(nextSession);
                nextSession.inspect(pattern, inputs, 10L);
                assertNotEquals(field(requests.get(0), "nonce"), field(requests.get(2), "nonce"));
                assertEquals(2L, input.get(AEItemKey.of(Items.IRON_INGOT)));
            }
        }
    }

    @Test
    void failedOrInvalidSubmissionIsNeverReportedAsSafeRejection() throws Exception {
        Class<?> api = optionalApi(LIGHTNING);
        try (var loader = mockStatic(ModList.class)) {
            ModList mods = mock(ModList.class);
            loader.when(ModList::get).thenReturn(mods);
            when(mods.isLoaded("ae2lt")).thenReturn(true);
            var pattern = mock(IPatternDetails.class);
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
            for (boolean throwsAfterAcceptance : new boolean[]{true, false}) {
                Object provider = Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (p, method, args) -> {
                    if (throwsAfterAcceptance) throw new IllegalStateException("failed after accepting inputs");
                    return record(method.getReturnType(), Map.of("acceptedAmount", 3L,
                            "unacceptedAmount", 10L, "resultSnapshot", List.of(), "retryable", false));
                });
                var session = ECOAe2LtBatchCapability.open(provider);
                assertNotNull(session);
                assertThrows(IllegalStateException.class, () -> session.submit(pattern, new KeyCounter[0], 10L));
            }
        }
    }

    private static Class<?> optionalApi(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException absent) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "Selected AE2LT predates this API; validate with -Pae2lt_local_jar pointing to a current build");
            throw absent;
        }
    }

    private static Object field(Object record, String name) throws Exception {
        return record.getClass().getMethod(name).invoke(record);
    }

    private static Object record(Class<?> type, Map<String, Object> values) throws Exception {
        var components = type.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            var component = components[index];
            args[index] = component.getType().isEnum()
                    ? Arrays.stream(component.getType().getEnumConstants())
                            .filter(e -> ((Enum<?>) e).name().equals(component.getName().equals("status") ? "PARTIAL" : "NONE"))
                            .findFirst().orElseThrow()
                    : values.get(component.getName());
        }
        return type.getConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new))
                .newInstance(args);
    }
}
