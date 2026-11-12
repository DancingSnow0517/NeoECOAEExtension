package cn.dancingsnow.neoecoae.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternWrappers;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** A task-local processing policy. Registered patterns and later interface edits remain independent. */
public final class ECOComponentInsensitiveProcessingPattern implements IPatternDetails, ECOPatternWrappers.Wrapper {
    private static final String POLICY = "neoecoae:processing_components";
    private final AEProcessingPattern delegate;
    private final Set<Identifier> items;
    private final IInput[] inputs;
    private final Map<ICraftingProvider, ICraftingProvider> providers = new IdentityHashMap<>();
    private AEItemKey definition;

    private ECOComponentInsensitiveProcessingPattern(AEProcessingPattern delegate, Set<Identifier> items) {
        this.delegate = delegate;
        this.items = Set.copyOf(items);
        var original = delegate.getInputs();
        this.inputs = new IInput[original.length];
        for (int slot = 0; slot < original.length; slot++) {
            var input = original[slot];
            if (!ECOProcessingComponentMatching.enabled(delegate, input.getPossibleInputs()[0].what(), items)) {
                inputs[slot] = input;
                continue;
            }
            inputs[slot] = new IInput() {
                public GenericStack[] getPossibleInputs() { return input.getPossibleInputs(); }
                public long getMultiplier() { return input.getMultiplier(); }
                public AEKey getRemainingKey(AEKey key) { return input.getRemainingKey(key); }
                public boolean isValid(AEKey key, Level level) {
                    return ECOProcessingComponentMatching.sameItem(input.getPossibleInputs()[0].what(), key);
                }
            };
        }
    }

    public static IPatternDetails of(IPatternDetails pattern, Set<Identifier> marked) {
        if (pattern == null || pattern.getClass() != AEProcessingPattern.class || marked.isEmpty()) return pattern;
        Set<Identifier> relevant = new LinkedHashSet<>();
        for (var input : pattern.getInputs()) {
            if (input.getPossibleInputs()[0].what() instanceof AEItemKey item) {
                var id = BuiltInRegistries.ITEM.getKey(item.getItem());
                if (marked.contains(id)) relevant.add(id);
            }
        }
        return relevant.isEmpty() ? pattern
            : new ECOComponentInsensitiveProcessingPattern((AEProcessingPattern) pattern, relevant);
    }

    public boolean ignoresInputComponents(int slot) {
        return slot >= 0 && slot < inputs.length
            && ECOProcessingComponentMatching.enabled(delegate, inputs[slot].getPossibleInputs()[0].what(), items);
    }

    @Override public IInput[] getInputs() { return inputs.clone(); }
    @Override public List<GenericStack> getOutputs() { return delegate.getOutputs(); }
    @Override public IPatternDetails ecoProviderPattern() { return delegate; }
    @Override public boolean supportsPushInputsToExternalInventory() { return true; }
    @Override public void pushInputsToExternalInventory(KeyCounter[] holders, PatternInputSink sink) {
        try (var scope = ECOProcessingComponentMatching.openDispatch(items)) {
            // Validate all concrete variants before the first physical insertion.
            if (!ECOProcessingComponentMatching.pushInputs(delegate, holders, sink)) {
                delegate.pushInputsToExternalInventory(holders, sink);
            }
        }
    }

    /** Keep this wrapper visible to foreign CPUs; provider delegation is resolved by ECO's service hook. */
    public synchronized ICraftingProvider provider(ICraftingProvider physical) {
        return providers.computeIfAbsent(physical, source -> {
            // Keep existing batch, deferred-output and job-aware provider subcontracts without
            // linking common code to optional mods. Provider identity is stable for this task.
            Set<Class<?>> contracts = new LinkedHashSet<>();
            contracts.add(ICraftingProvider.class);
            for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass()) {
                for (Class<?> contract : type.getInterfaces()) addProviderContract(contracts, contract);
            }
            return (ICraftingProvider) Proxy.newProxyInstance(source.getClass().getClassLoader(),
                contracts.toArray(Class<?>[]::new), (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            default -> source.toString();
                        };
                    }
                    Object[] forwarded = args == null ? null : args.clone();
                    if (forwarded != null) {
                        for (int i = 0; i < forwarded.length; i++) {
                            if (forwarded[i] == this) forwarded[i] = delegate;
                        }
                    }
                    try (var scope = ECOProcessingComponentMatching.openDispatch(items)) {
                        Object result = invoke(source, method, forwarded);
                        // Prepared admission commits after the provider call has returned. Carry
                        // the same policy into that commit instead of consulting live interfaces.
                        if (result != null && method.getName().equals("prepareBatch") && method.getReturnType().isInterface()) {
                            return Proxy.newProxyInstance(source.getClass().getClassLoader(),
                                new Class<?>[] {method.getReturnType()}, (prepared, operation, values) -> {
                                    try (var commitScope = ECOProcessingComponentMatching.openDispatch(items)) {
                                        return invoke(result, operation, values);
                                    }
                                });
                        }
                        return result;
                    }
                });
        });
    }

    private static void addProviderContract(Set<Class<?>> contracts, Class<?> contract) {
        if (Modifier.isPublic(contract.getModifiers()) && !contract.isSealed()
                && ICraftingProvider.class.isAssignableFrom(contract)) {
            contracts.add(contract);
        } else {
            for (Class<?> parent : contract.getInterfaces()) addProviderContract(contracts, parent);
        }
    }

    private static Object invoke(Object receiver, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException failed) {
            throw failed.getCause();
        }
    }

    @Override public AEItemKey getDefinition() {
        if (definition == null) {
            var stack = delegate.getDefinition().toStack();
            var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (data.contains(POLICY)) throw new IllegalArgumentException("Nested processing component policy");
            var policy = new CompoundTag();
            policy.putBoolean("hadCustomData", stack.has(DataComponents.CUSTOM_DATA));
            var ids = new ListTag();
            items.stream().sorted().forEach(id -> ids.add(StringTag.valueOf(id.toString())));
            policy.put("items", ids);
            data.put(POLICY, policy);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            definition = AEItemKey.of(stack);
        }
        return definition;
    }

    /** Called after ordinary AE2 decoding, so no extra item or competing pattern decoder is needed. */
    public static IPatternDetails restore(IPatternDetails decoded) {
        if (decoded == null || decoded.getClass() != AEProcessingPattern.class) return decoded;
        var stack = decoded.getDefinition().toStack();
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(POLICY)) return decoded;
        if (!data.contains(POLICY)) throw new IllegalArgumentException("Invalid processing policy");
        var policy = data.getCompoundOrEmpty(POLICY);
        if (!policy.contains("items")) throw new IllegalArgumentException("Missing processing items");
        if (!(policy.get("items") instanceof ListTag raw) || !raw.stream().allMatch(net.minecraft.nbt.StringTag.class::isInstance)) {
            throw new IllegalArgumentException("Invalid processing item list");
        }
        var ids = policy.getListOrEmpty("items");
        Set<Identifier> items = new LinkedHashSet<>();
        for (int i = 0; i < ids.size(); i++) items.add(Identifier.parse(ids.getStringOr(i, "")));
        data.remove(POLICY);
        if (!policy.getBooleanOr("hadCustomData", false) && data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        var restored = of(new AEProcessingPattern(AEItemKey.of(stack)), items);
        if (!(restored instanceof ECOComponentInsensitiveProcessingPattern)) {
            throw new IllegalArgumentException("Empty processing component policy");
        }
        return restored;
    }
}
