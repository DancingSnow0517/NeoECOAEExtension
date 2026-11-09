import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.logging.LoggerAdapterDefault;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.util.ReEntranceLock;

/** Runs the real Mixin transformer on pinned classes without launching Minecraft or opening a world. */
public final class WeaveCounter {
    private record Property(String name) implements IPropertyKey {}

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        ClassLoader loader = WeaveCounter.class.getClassLoader();
        Map<IPropertyKey, Object> properties = new HashMap<>();
        var global = proxy(IGlobalPropertyService.class, (p, method, a) -> switch (method.getName()) {
            case "resolveKey" -> new Property((String) a[0]);
            case "setProperty" -> { properties.put((IPropertyKey) a[0], a[1]); yield null; }
            case "getProperty", "getPropertyString" -> properties.getOrDefault(a[0], a.length > 1 ? a[1] : null);
            default -> throw new UnsupportedOperationException(method.toString());
        });
        var classes = proxy(IClassProvider.class, (p, method, a) -> switch (method.getName()) {
            case "getClassPath" -> new java.net.URL[0];
            case "findClass", "findAgentClass" -> Class.forName((String) a[0], a.length > 1 && (boolean) a[1], loader);
            default -> throw new UnsupportedOperationException(method.toString());
        });
        var bytecode = proxy(IClassBytecodeProvider.class, (p, method, a) -> {
            String resource = ((String) a[0]).replace('.', '/') + ".class";
            try (InputStream input = loader.getResourceAsStream(resource)) {
                if (input == null) throw new ClassNotFoundException(resource);
                var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
            }
        });
        var transforms = proxy(ITransformerProvider.class, (p, method, a) ->
            method.getName().equals("addTransformerExclusion") ? null : List.of());
        var tracker = proxy(IClassTracker.class, (p, method, a) -> switch (method.getName()) {
            case "isClassLoaded" -> false;
            case "getClassRestrictions" -> "";
            default -> null;
        });
        List<IMixinInternal> internals = new ArrayList<>();
        var lock = new ReEntranceLock(1);
        var container = new ContainerHandleVirtual("storage-counter-check");
        var service = proxy(IMixinService.class, (p, method, a) -> switch (method.getName()) {
            case "getName" -> "StorageCounterCheck";
            case "isValid" -> true;
            case "getInitialPhase" -> MixinEnvironment.Phase.DEFAULT;
            case "getMinCompatibilityLevel", "getMaxCompatibilityLevel" -> MixinEnvironment.CompatibilityLevel.JAVA_17;
            case "getSideName" -> "SERVER";
            case "getLogger" -> new LoggerAdapterDefault((String) a[0]);
            case "getReEntranceLock" -> lock;
            case "getClassProvider" -> classes;
            case "getBytecodeProvider" -> bytecode;
            case "getTransformerProvider" -> transforms;
            case "getClassTracker" -> tracker;
            case "getPrimaryContainer" -> container;
            case "getPlatformAgents", "getMixinContainers" -> List.of();
            case "getResourceAsStream" -> loader.getResourceAsStream((String) a[0]);
            case "offer" -> { internals.add((IMixinInternal) a[0]); yield null; }
            case "toString" -> "StorageCounterCheck";
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            case "prepare", "init", "beginPhase", "checkEnv", "getAuditTrail" -> null;
            default -> throw new UnsupportedOperationException(method.toString());
        });
        Method instanceMethod = MixinService.class.getDeclaredMethod("getInstance");
        instanceMethod.setAccessible(true);
        Object instance = instanceMethod.invoke(null);
        field(instance, "service", service);
        field(instance, "propertyService", global);
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.SERVER);
        var factory = internals.stream().filter(IMixinTransformerFactory.class::isInstance)
            .map(IMixinTransformerFactory.class::cast).findFirst().orElseThrow();
        var transformer = factory.createTransformer();
        MixinEnvironment.getDefaultEnvironment().setActiveTransformer(transformer);
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        Mixins.addConfiguration(args.length > 1 ? args[1] : "counter-only.mixins.json");
        String[] targets = args.length > 2 ? java.util.Arrays.copyOfRange(args, 2, args.length)
            : new String[]{"appeng.api.stacks.KeyCounter"};
        for (String target : targets) {
        byte[] original;
        try (InputStream input = loader.getResourceAsStream(target.replace('.', '/') + ".class")) {
            original = input.readAllBytes();
        }
        byte[] transformed = transformer.transformClassBytes(target, target, original);
        if (java.util.Arrays.equals(original, transformed)) throw new AssertionError(target + " was not transformed");
        var node = new ClassNode(); new ClassReader(transformed).accept(node, 0);
        if (target.equals("appeng.api.stacks.KeyCounter")
                && node.interfaces.stream().noneMatch(name -> name.endsWith("TerminalInventorySnapshot$SnapshotCounter")))
            throw new AssertionError("Snapshot interface missing");
        Path file = output.resolve(target.replace('.', '/') + ".class");
        Files.createDirectories(file.getParent()); Files.write(file, transformed);
        System.out.println("Woven pinned class: " + file + " (" + transformed.length + " bytes)");
        }
    }
}
