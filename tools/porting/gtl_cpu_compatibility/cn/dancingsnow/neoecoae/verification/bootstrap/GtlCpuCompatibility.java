package cn.dancingsnow.neoecoae.verification.bootstrap;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

/** Loads real dependency bytecode without starting Minecraft or opening a save. */
public final class GtlCpuCompatibility {
    private static final String TARGET = "appeng.crafting.execution.CraftingCpuLogic";
    private static final String MIXIN =
        "cn/dancingsnow/neoecoae/mixins/ae2/crafting/Ae2CraftingCpuFastPathMixin.class";
    private static final String GTL_MIXIN = "org.gtlcore.gtlcore.mixin.ae2.logic.CraftingCpuLogicMixin";

    private GtlCpuCompatibility() {}

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        var urls = new ArrayList<URL>();
        Path overrides = Path.of("overrides").toAbsolutePath();
        Files.createDirectories(overrides);
        Path logging = overrides.resolve("log4j2-regression.xml");
        Files.writeString(logging, """
            <Configuration status="ERROR">
              <Appenders><Console name="Console"><PatternLayout pattern="%level %msg%n"/></Console></Appenders>
              <Loggers><Root level="info"><AppenderRef ref="Console"/></Root></Loggers>
            </Configuration>
            """);
        System.setProperty("log4j.configurationFile", logging.toUri().toString());
        if (mode.equals("before")) restoreOldPriority(overrides);
        Files.writeString(overrides.resolve("gtl-cpu-regression.mixins.json"), """
            {
              "required": true,
              "minVersion": "0.8",
              "compatibilityLevel": "JAVA_17",
              "package": "org.gtlcore.gtlcore.mixin.ae2.logic",
              "mixins": ["CraftingCpuLogicMixin"]
            }
            """);
        urls.add(overrides.toUri().toURL());
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(Path.of(entry).toUri().toURL());
        }
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("TweakClasses", new ArrayList<String>());
        Launch.blackboard.put("Tweaks", new ArrayList<>());
        Launch.blackboard.put("ArgumentList", new ArrayList<String>());
        Launch.minecraftHome = Path.of(".").toFile();
        URL regressionContainer = overrides.toUri().toURL();
        Launch.classLoader = new LaunchClassLoader(urls.toArray(URL[]::new)) {
            @Override public List<URL> getSources() {
                // Register only the explicit regression configs, not every addon's manifest.
                return List.of(regressionContainer);
            }
        };
        for (String prefix : List.of("org.spongepowered.asm.", "org.objectweb.asm.",
                "com.llamalad7.mixinextras.", "org.slf4j.", "com.mojang.logging.",
                "cn.dancingsnow.neoecoae.verification.bootstrap.")) {
            Launch.classLoader.addClassLoaderExclusion(prefix);
        }
        Thread.currentThread().setContextClassLoader(Launch.classLoader);
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.SERVER);
        MixinExtrasBootstrap.init();
        Mixins.addConfiguration("neoecoae.mixins.json");
        if (!mode.equals("base")) Mixins.addConfiguration("gtl-cpu-regression.mixins.json");
        MixinBootstrap.getPlatform().inject();
        var changePhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        changePhase.setAccessible(true);
        changePhase.invoke(null, MixinEnvironment.Phase.DEFAULT);

        try {
            var transformed = Class.forName(TARGET, false, Launch.classLoader);
            if (mode.equals("before")) throw new AssertionError("Old priority unexpectedly accepted the GTLCore overwrite");
            var names = Arrays.stream(transformed.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
            for (String hook : List.of("neoecoae$dispatch", "neoecoae$pushWithJob", "neoecoae$releaseWorkerOutput")) {
                require(names.stream().anyMatch(name -> name.contains(hook)), "Missing transformed hook: " + hook);
            }
            verifyExport(mode);
            System.out.println("GTL_CPU_COMPAT PASS mode=" + mode + " transformed=true jobWrapper=true dispatch=true lifecycle=true");
        } catch (Throwable failure) {
            if (!mode.equals("before") || !isOriginalFailure(failure)) throw failure;
            System.out.println("GTL_CPU_COMPAT PASS mode=before reproduced=InvalidInjectionException priority1000_vs_1100");
        }
    }

    private static void restoreOldPriority(Path overrides) throws Exception {
        var node = new ClassNode();
        try (var input = GtlCpuCompatibility.class.getClassLoader().getResourceAsStream(MIXIN)) {
            if (input == null) throw new IllegalStateException("Missing production Mixin bytecode");
            new ClassReader(input).accept(node, 0);
        }
        var annotation = node.invisibleAnnotations.stream()
            .filter(value -> value.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        int index = annotation.values.indexOf("priority");
        if (index < 0) throw new IllegalStateException("Production Mixin must declare its compatibility priority");
        annotation.values.set(index + 1, 1000);
        var writer = new ClassWriter(0);
        node.accept(writer);
        Path target = overrides.resolve(MIXIN);
        Files.createDirectories(target.getParent());
        Files.write(target, writer.toByteArray());
    }

    private static void verifyExport(String mode) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class", TARGET.replace('.', '/') + ".class"))).accept(node, 0);
        var execute = node.methods.stream().filter(method -> method.name.equals("executeCrafting")).findFirst().orElseThrow();
        boolean dispatch = false;
        boolean wrappedPush = false;
        for (var instruction : execute.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            dispatch |= call.name.contains("neoecoae$dispatch");
            wrappedPush |= call.name.contains("neoecoae$pushWithJob");
            require(!(call.name.equals("pushPattern") && call.owner.equals("appeng/api/networking/crafting/ICraftingProvider")),
                "Unwrapped provider push remains in executeCrafting");
        }
        require(dispatch && wrappedPush, "The final executeCrafting body must retain both ECO dispatch and the job wrapper");
        boolean overwrittenByGtl = execute.visibleAnnotations != null && execute.visibleAnnotations.stream()
            .anyMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                && annotation.values.contains(GTL_MIXIN));
        require(overwrittenByGtl == !mode.equals("base"), "Unexpected owner of the transformed executeCrafting body");
    }

    private static boolean isOriginalFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains("priority 1000 cannot inject into")
                    && message.contains(GTL_MIXIN) && message.contains("priority 1100")) return true;
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
