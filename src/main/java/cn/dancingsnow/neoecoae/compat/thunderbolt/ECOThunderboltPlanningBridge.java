package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.crafting.ECOComponentInsensitiveProcessingPattern;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

/** Captures live interface settings on the Grid thread and adapts only a foreign calculation's pattern view. */
public final class ECOThunderboltPlanningBridge {
    private ECOThunderboltPlanningBridge() {}

    public static Set<Identifier> capture(ICraftingService service) {
        return service instanceof ECOCraftingNetworkSettings settings
            ? Set.copyOf(settings.neoecoae$getFuzzyPlanningItemIds()) : Set.of();
    }

    public static ICraftingService forCalculation(ICraftingService service, Set<Identifier> items) {
        if (items.isEmpty()) return service;
        Set<Identifier> snapshot = Set.copyOf(items);
        Map<IPatternDetails, IPatternDetails> patterns = new IdentityHashMap<>();
        return (ICraftingService) Proxy.newProxyInstance(ICraftingService.class.getClassLoader(),
            new Class<?>[] {ICraftingService.class}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> service.toString();
                    };
                }
                try {
                    Object result = method.invoke(service, args);
                    if (method.getName().equals("getCraftingFor") && result instanceof Collection<?> offered) {
                        return offered.stream().map(IPatternDetails.class::cast).map(pattern ->
                            patterns.computeIfAbsent(pattern,
                                source -> ECOComponentInsensitiveProcessingPattern.of(source, snapshot))).toList();
                    }
                    return result;
                } catch (InvocationTargetException failed) {
                    throw failed.getCause();
                }
            });
    }
}
