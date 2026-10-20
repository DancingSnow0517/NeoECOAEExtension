package cn.dancingsnow.neoecoae.mixins.compat.useless;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionRequirement;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class UselessSmartDoublingPlansMixinTest {
    private static final String SUBMISSION_MIXIN =
        "com/sorrowmist/useless/mixin/ae2/CraftingServiceSmartDoublingMixin.class";
    private static final String PLANS =
        "com/sorrowmist/useless/content/machines/advanced_alloy_furnace/ae/SmartDoublingPlans";

    @Test
    void everyReleasedSubmissionEntryHasAnExplicitCompatibleHook() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(SUBMISSION_MIXIN)) {
            assertNotNull(stream);
            assertProtectedCalls(stream);
        }
        // Also inspect the player's actual release when reproducing a version-specific overload change.
        String installedJar = System.getenv("ECO_USELESS_CONTRACT_JAR");
        if (installedJar != null && !installedJar.isBlank()) {
            try (ZipFile jar = new ZipFile(Path.of(installedJar).toFile());
                    InputStream stream = jar.getInputStream(jar.getEntry(SUBMISSION_MIXIN))) {
                assertProtectedCalls(stream);
            }
        }
    }

    private static void assertProtectedCalls(InputStream stream) throws Exception {
        var caller = new ClassNode();
        new ClassReader(stream).accept(caller, 0);
        int calls = 0;
        for (var method : caller.methods) {
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)
                        || !call.owner.equals(PLANS) || !call.name.equals("rewriteForSubmission")) continue;
                calls++;
                String selector = call.name + call.desc;
                Method hook = hooks().stream().filter(candidate ->
                    Arrays.asList(candidate.getAnnotation(Inject.class).method()).contains(selector))
                    .findFirst().orElseThrow(() -> new AssertionError("Unprotected release entry: " + selector));
                Type[] targetArguments = Type.getArgumentTypes(call.desc);
                Class<?>[] hookArguments = hook.getParameterTypes();
                assertEquals(targetArguments.length + 1, hookArguments.length);
                for (int i = 0; i < targetArguments.length; i++) {
                    assertEquals(targetArguments[i], Type.getType(hookArguments[i]));
                }
                assertEquals(CallbackInfoReturnable.class, hookArguments[hookArguments.length - 1]);
                assertTrue(hook.getAnnotation(Inject.class).cancellable());
            }
        }
        assertTrue(calls > 0, "Release must call the smart-doubling submission rewriter");
    }

    @Test
    void bothOverloadsPreserveOnlyTheConfirmedTaskVector() throws Exception {
        AEKey output = mock(AEKey.class);
        CraftingPlan confirmed = plan(output, 4444);
        CraftingPlan different = plan(output, 4445);
        ECOPlanningResult result = mock(ECOPlanningResult.class);
        when(result.plan()).thenReturn(confirmed);
        when(result.status()).thenReturn(PlanningStatus.SUCCESS);
        when(result.executionRequirement()).thenReturn(ECOExecutionRequirement.ORDERED);
        when(result.planningId()).thenReturn(UUID.randomUUID());

        for (Method hook : hooks()) {
            assertFalse(invoke(hook, confirmed).isCancelled(), "Foreign submissions retain Useless rewriting");
            ECOPlanningResultRegistry.withSubmissionAlias(confirmed, result, () -> {
                try {
                    var callback = invoke(hook, confirmed);
                    assertTrue(callback.isCancelled());
                    assertSame(confirmed, callback.getReturnValue());
                    assertFalse(invoke(hook, different).isCancelled(), "Output alone cannot transfer ownership");
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
                return null;
            });
            assertFalse(invoke(hook, confirmed).isCancelled(), "Submission binding must be scoped");
        }
        assertEquals(2, hooks().size());
    }

    private static CraftingPlan plan(AEKey output, long count) {
        CraftingPlan plan = mock(CraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new GenericStack(output, count));
        when(plan.patternTimes()).thenReturn(Map.of());
        when(plan.usedItems()).thenReturn(new KeyCounter());
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        when(plan.missingItems()).thenReturn(new KeyCounter());
        return plan;
    }

    private static List<Method> hooks() {
        return Arrays.stream(UselessSmartDoublingPlansMixin.class.getDeclaredMethods())
            .filter(method -> method.getAnnotation(Inject.class) != null).toList();
    }

    private static CallbackInfoReturnable<ICraftingPlan> invoke(Method hook, ICraftingPlan plan)
            throws ReflectiveOperationException {
        hook.setAccessible(true);
        var callback = new CallbackInfoReturnable<ICraftingPlan>("rewriteForSubmission", true);
        Object[] arguments = new Object[hook.getParameterCount()];
        arguments[0] = plan;
        arguments[arguments.length - 1] = callback;
        hook.invoke(null, arguments);
        return callback;
    }
}
