package cn.dancingsnow.neoecoae.integration.ae2vm;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional integration kept free of static AE2-VM type references. */
public final class AE2VMPlanningBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final String API_CLASS = "com.ae2vm.addon.api.AE2VMCrafting";
    private static volatile Method calculateMethod;

    private AE2VMPlanningBridge() {
    }

    public static boolean isEnabled() {
        return NEConfig.useAE2VMPlanning && ModList.get().isLoaded("ae2vm");
    }

    public static Future<ICraftingPlan> planVMOrNative(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey what,
        long amount,
        CalculationStrategy strategy,
        Supplier<Future<ICraftingPlan>> nativeFallback
    ) {
        if (!isEnabled()) {
            return nativeFallback.get();
        }

        CompletableFuture<ICraftingPlan> vmPlan;
        try {
            vmPlan = invoke(grid, requester, what, amount, strategy);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LOGGER.warn("AE2-VM planning integration could not start; using native AE2 planning", failure);
            return nativeFallback.get();
        }

        RoutingFuture result = new RoutingFuture();
        result.activeTask.set(vmPlan);
        vmPlan.whenComplete((plan, failure) -> {
            if (result.isCancelled()) {
                return;
            }
            if (failure == null && plan != null) {
                result.complete(plan);
                return;
            }
            if (failure != null) {
                LOGGER.warn("AE2-VM planning failed; using native AE2 planning", unwrap(failure));
            } else {
                LOGGER.warn("AE2-VM returned no crafting plan; using native AE2 planning");
            }
            Future<ICraftingPlan> fallback;
            try {
                fallback = nativeFallback.get();
            } catch (RuntimeException fallbackFailure) {
                result.completeExceptionally(fallbackFailure);
                return;
            }
            if (!result.activate(fallback)) {
                return;
            }
            await(fallback).whenComplete((fallbackPlan, fallbackFailure) -> {
                if (fallbackFailure == null) {
                    result.complete(fallbackPlan);
                } else {
                    result.completeExceptionally(unwrap(fallbackFailure));
                }
            });
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<ICraftingPlan> invoke(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey what,
        long amount,
        CalculationStrategy strategy
    ) throws ReflectiveOperationException {
        Method method = calculateMethod;
        if (method == null) {
            synchronized (AE2VMPlanningBridge.class) {
                method = calculateMethod;
                if (method == null) {
                    Class<?> api = Class.forName(API_CLASS, true, AE2VMPlanningBridge.class.getClassLoader());
                    method = api.getMethod("calculate", IGrid.class, ICraftingSimulationRequester.class,
                        AEKey.class, long.class, CalculationStrategy.class);
                    calculateMethod = method;
                }
            }
        }
        try {
            Object result = method.invoke(null, grid, requester, what, amount, strategy);
            if (result instanceof CompletableFuture<?> future) {
                return (CompletableFuture<ICraftingPlan>) future;
            }
            throw new ReflectiveOperationException("AE2VMCrafting.calculate returned "
                + (result == null ? "null" : result.getClass().getName()));
        } catch (InvocationTargetException failure) {
            throw new ReflectiveOperationException("AE2VMCrafting.calculate failed", failure.getCause());
        }
    }

    private static CompletableFuture<ICraftingPlan> await(Future<ICraftingPlan> future) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return future.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CompletionException(interrupted);
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        });
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null
            ? failure.getCause()
            : failure;
    }

    private static final class RoutingFuture extends CompletableFuture<ICraftingPlan> {
        private final AtomicReference<Future<?>> activeTask = new AtomicReference<>();

        private RoutingFuture() {
        }

        private boolean activate(Future<?> task) {
            activeTask.set(task);
            if (isCancelled()) {
                task.cancel(true);
                return false;
            }
            return true;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            Future<?> task = activeTask.get();
            if (task != null) {
                task.cancel(mayInterruptIfRunning);
            }
            return cancelled;
        }
    }
}
