package cn.dancingsnow.neoecoae.jeismoke;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;

/** Applies the real production Mixins to the selected release JAR, then asks the JVM to verify them. */
public final class JeiLayoutMixinSmoke {
    private static final ClassLoader SOURCE = JeiLayoutMixinSmoke.class.getClassLoader();
    private static final List<String> TARGETS = List.of(
        "mezz.jei.library.gui.recipes.RecipeLayout",
        "mezz.jei.library.gui.recipes.layout.builder.RecipeLayoutBuilder");

    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        var service = (Service) MixinService.getService();
        IMixinTransformer transformer = service.createTransformer();
        MixinExtrasBootstrap.init();
        MixinEnvironment.getCurrentEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("jei-layout-smoke.mixins.json");
        Map<String, byte[]> transformed = new HashMap<>();
        for (String name : TARGETS) {
            byte[] original = bytes(name);
            byte[] result = transformer.transformClassBytes(name, name, original);
            ClassNode type = new ClassNode();
            new ClassReader(result).accept(type, 0);
            if (Arrays.equals(original, result) || type.methods.stream().noneMatch(method ->
                    method.name.contains("neoecoae$recipeHeight"))) {
                throw new AssertionError("Production height Mixin was not applied to " + name);
            }
            if (type.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray()))
                    .noneMatch(instruction -> instruction instanceof MethodInsnNode call
                        && call.name.contains("neoecoae$recipeHeight"))) {
                throw new AssertionError("Height handler is not invoked in " + name);
            }
            transformed.put(name, result);
            Path output = Path.of(args[0], name.replace('.', '/') + ".class");
            Files.createDirectories(output.getParent());
            Files.write(output, result);
        }
        ClassLoader verified = new ClassLoader(SOURCE) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                // Keep JEI's self references in the same loader as the transformed targets.
                if (!name.startsWith("mezz.jei.")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try {
                            byte[] code = transformed.containsKey(name) ? transformed.get(name) : bytes(name);
                            loaded = defineClass(name, code, 0, code.length);
                        } catch (IOException error) {
                            throw new ClassNotFoundException(name, error);
                        }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        for (String name : TARGETS) {
            Class<?> type = verified.loadClass(name);
            // Resolving declared members triggers verification, including constructor bytecode.
            type.getDeclaredConstructors();
            type.getDeclaredMethods();
            System.out.println("JEI_MIXIN_VERIFIED " + name);
        }
    }

    private static byte[] bytes(String name) throws IOException {
        try (InputStream input = SOURCE.getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) throw new IOException("Missing class " + name);
            return input.readAllBytes();
        }
    }

    public static final class Service extends MixinServiceAbstract
            implements IClassProvider, IClassBytecodeProvider, IClassTracker {
        @Override public String getName() { return "NeoECOAE JEI verification"; }
        @Override public boolean isValid() { return true; }
        @Override public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }
        @Override public IClassProvider getClassProvider() { return this; }
        @Override public IClassBytecodeProvider getBytecodeProvider() { return this; }
        @Override public ITransformerProvider getTransformerProvider() { return null; }
        @Override public IClassTracker getClassTracker() { return this; }
        @Override public IMixinAuditTrail getAuditTrail() { return null; }
        @Override public Collection<String> getPlatformAgents() { return List.of(); }
        @Override public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual(getName()); }
        @Override public InputStream getResourceAsStream(String name) { return SOURCE.getResourceAsStream(name); }
        @Override public URL[] getClassPath() { return new URL[0]; }
        @Override public Class<?> findClass(String name) throws ClassNotFoundException {
            return findClass(name, false);
        }
        @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
            return Class.forName(name, initialize, SOURCE);
        }
        @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
            return findClass(name, initialize);
        }
        @Override public ClassNode getClassNode(String name) throws IOException {
            return getClassNode(name, true);
        }
        @Override public ClassNode getClassNode(String name, boolean runTransformers) throws IOException {
            ClassNode type = new ClassNode();
            new ClassReader(bytes(name.replace('/', '.'))).accept(type, ClassReader.EXPAND_FRAMES);
            return type;
        }
        @Override public void registerInvalidClass(String name) { }
        @Override public boolean isClassLoaded(String name) { return false; }
        @Override public String getClassRestrictions(String name) { return ""; }
        IMixinTransformer createTransformer() {
            return getInternal(IMixinTransformerFactory.class).createTransformer();
        }
    }

    public static final class Properties implements IGlobalPropertyService {
        private final Map<IPropertyKey, Object> values = new HashMap<>();
        private record Key(String name) implements IPropertyKey { }
        @Override public IPropertyKey resolveKey(String name) { return new Key(name); }
        @Override @SuppressWarnings("unchecked")
        public <T> T getProperty(IPropertyKey key) { return (T) values.get(key); }
        @Override public void setProperty(IPropertyKey key, Object value) { values.put(key, value); }
        @Override public <T> T getProperty(IPropertyKey key, T fallback) {
            T value = getProperty(key);
            return value == null ? fallback : value;
        }
        @Override public String getPropertyString(IPropertyKey key, String fallback) {
            Object value = values.get(key);
            return value == null ? fallback : value.toString();
        }
    }
}
