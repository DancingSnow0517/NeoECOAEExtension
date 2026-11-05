package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.helpers.MultiCraftingTracker;
import cn.dancingsnow.neoecoae.mixins.compat.merequester.RequesterPlanningMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class AutomaticCraftingContractTest {
    @Test void releasedCardAndRequesterBytecodeContainTheExactPlanningCallSites() throws Exception {
        assertPlanningTarget(MultiCraftingTracker.class, MultiCraftingTrackerMixin.class, "handleCrafting");
        // Read the published optional JAR without initializing RequesterBlockEntity or linking client classes.
        assertPlanningTarget("com/almostreliable/merequester/requester/status/RequestState",
                RequesterPlanningMixin.class, "handle");
    }

    @Test void failedCalculationReleasesItsSlotWithoutCancellingOtherOrders() throws Throwable {
        var failed = CompletableFuture.<ICraftingPlan>failedFuture(new IllegalStateException("planning failed"));
        var other = new CompletableFuture<ICraftingPlan>();
        var tracker = tracker(failed, other);
        assertNull(consume(tracker, failed));
        var jobs = jobs(tracker);
        assertNull(jobs[0]);
        assertSame(other, jobs[1]);
        assertFalse(other.isCancelled());
    }

    @Test void cancelledCalculationDoesNotPermanentlyOccupyTheCraftingCard() throws Throwable {
        var cancelled = new CompletableFuture<ICraftingPlan>();
        cancelled.cancel(true);
        var tracker = tracker(cancelled);
        assertNull(consume(tracker, cancelled));
        assertNull(jobs(tracker));
    }

    @Test void completedPlanIsReturnedToTheNativeSubmissionCode() throws Throwable {
        var plan = mock(ICraftingPlan.class);
        var ready = CompletableFuture.completedFuture(plan);
        var tracker = tracker(ready);
        assertSame(plan, consume(tracker, ready));
        assertSame(ready, jobs(tracker)[0]);
    }

    @Test void interruptedCalculationReleasesItsSlotAndPreservesTheInterrupt() throws Throwable {
        var future = mock(Future.class);
        when(future.get()).thenThrow(new InterruptedException());
        var tracker = tracker(future);
        try {
            assertNull(consume(tracker, future));
            assertTrue(Thread.currentThread().isInterrupted());
            assertNull(jobs(tracker));
        } finally { Thread.interrupted(); }
    }

    private static Object consume(MultiCraftingTrackerMixin tracker, Future<?> future) throws Throwable {
        var method = MultiCraftingTrackerMixin.class.getDeclaredMethod("neoecoae$releaseFailedCalculation",
                Future.class, Operation.class, int.class);
        method.setAccessible(true);
        Operation<Object> original = mock(Operation.class);
        when(original.call(future)).thenAnswer(call -> future.get());
        try { return method.invoke(tracker, future, original, 0); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    @SafeVarargs private static MultiCraftingTrackerMixin tracker(Future<ICraftingPlan>... futures) throws Exception {
        var tracker = mock(MultiCraftingTrackerMixin.class, CALLS_REAL_METHODS);
        var field = MultiCraftingTrackerMixin.class.getDeclaredField("jobs");
        field.setAccessible(true);
        field.set(tracker, futures);
        return tracker;
    }

    private static Future<?>[] jobs(MultiCraftingTrackerMixin tracker) throws Exception {
        var field = MultiCraftingTrackerMixin.class.getDeclaredField("jobs");
        field.setAccessible(true);
        return (Future<?>[]) field.get(tracker);
    }

    private static void assertPlanningTarget(Class<?> target, Class<?> mixin, String entry) throws Exception {
        assertPlanningTarget(target.getName().replace('.', '/'), mixin, entry);
    }

    private static void assertPlanningTarget(String target, Class<?> mixin, String entry) throws Exception {
        var hook = java.util.Arrays.stream(mixin.getDeclaredMethods())
                .filter(method -> method.getName().equals("neoecoae$planAutomaticOrder")).findFirst().orElseThrow();
        var annotation = hook.getAnnotation(WrapOperation.class);
        assertArrayEquals(new String[]{entry}, annotation.method());
        String invoke = annotation.at()[0].target();
        var node = new ClassNode();
        try (var input = mixin.getClassLoader().getResourceAsStream(target + ".class")) {
            assertNotNull(input, "Missing released integration target: " + target);
            new ClassReader(input).accept(node, 0);
        }
        long calls = node.methods.stream().filter(method -> method.name.equals(entry))
                .flatMap(method -> java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false))
                .filter(instruction -> instruction instanceof MethodInsnNode call
                        && invoke.equals("L" + call.owner + ";" + call.name + call.desc)).count();
        assertEquals(1, calls, "The released machine must still invoke the wrapped AE2 planning entry");
    }
}
