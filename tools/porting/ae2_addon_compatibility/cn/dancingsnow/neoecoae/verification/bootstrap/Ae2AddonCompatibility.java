package cn.dancingsnow.neoecoae.verification.bootstrap;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.InputStreamReader;
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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

/** Loads real dependency bytecode without starting Minecraft or opening a save. */
public final class Ae2AddonCompatibility {
    private static final String TARGET = "appeng.crafting.execution.CraftingCpuLogic";
    private static final String MIXIN =
        "cn/dancingsnow/neoecoae/mixins/ae2/crafting/Ae2CraftingCpuFastPathMixin.class";
    private static JsonObject sample;
    private static String addonMixin;
    private static Path overrideDirectory;

    private Ae2AddonCompatibility() {}

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        var runtimeSample = JsonParser.parseString(Files.readString(Path.of(args[3]))).getAsJsonObject();
        sample = runtimeSample.getAsJsonObject("targets").getAsJsonObject(args[1]);
        addonMixin = runtimeSample.get("mixin_package").getAsString() + "." + sample.get("mixin").getAsString();
        boolean selectionList = args.length > 1 && args[1].equals("selection-list");
        boolean craftConfirm = args.length > 1 && args[1].equals("craft-confirm");
        boolean craftingStyle = args.length > 1 && args[1].equals("crafting-style");
        boolean planningRoute = args.length > 1 && args[1].equals("planning-route");
        boolean submissionRoute = args.length > 1 && args[1].equals("submission-route");
        String target = craftingStyle ? "appeng.client.gui.style.StyleManager" :
            craftConfirm || planningRoute ? "appeng.menu.me.crafting.CraftConfirmMenu" :
            submissionRoute ? "appeng.me.service.CraftingService" :
            selectionList ? "appeng.client.gui.widgets.CPUSelectionList" : TARGET;
        var urls = new ArrayList<URL>();
        Path overrides = Files.createTempDirectory(Path.of(".").toAbsolutePath(), "overrides-");
        overrideDirectory = overrides;
        Path logging = overrides.resolve("log4j2-regression.xml");
        Files.writeString(logging, """
            <Configuration status="ERROR">
              <Appenders><Console name="Console"><PatternLayout pattern="%level %msg%n"/></Console></Appenders>
              <Loggers><Root level="info"><AppenderRef ref="Console"/></Root></Loggers>
            </Configuration>
            """);
        System.setProperty("log4j.configurationFile", logging.toUri().toString());
        if (mode.equals("before") && !craftingStyle) {
            if (craftConfirm) restoreOldConfirmationIds(overrides);
            else if (planningRoute) restoreOldPlanningEntries(overrides);
            else if (submissionRoute) restoreOldSubmissionPoint(overrides);
            else restoreOldPriority(overrides);
        }
        Files.writeString(overrides.resolve("ae2-addon-regression.mixins.json"), """
            {
              "required": true,
              "minVersion": "0.8",
              "compatibilityLevel": "JAVA_17",
              "package": "%s",
              "mixins": ["%s"]
            }
            """.formatted(runtimeSample.get("mixin_package").getAsString(), sample.get("mixin").getAsString()));
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
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(selectionList || craftingStyle ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);
        if ((selectionList || craftConfirm || planningRoute) && args.length > 2 && args[2].equals("searge")) environment.setObfuscationContext("searge");
        MixinExtrasBootstrap.init();
        Mixins.addConfiguration("neoecoae.mixins.json");
        if (!mode.equals("base")) Mixins.addConfiguration("ae2-addon-regression.mixins.json");
        MixinBootstrap.getPlatform().inject();
        var changePhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        changePhase.setAccessible(true);
        changePhase.invoke(null, MixinEnvironment.Phase.DEFAULT);

        try {
            var transformed = Class.forName(target, false, Launch.classLoader);
            if (craftingStyle) {
                verifyCraftingStyle(mode, target, transformed);
                return;
            }
            if (craftConfirm) {
                verifyConfirmationSync(mode, target, args.length <= 2 || !args[2].equals("searge"));
                return;
            }
            if (planningRoute) {
                verifyPlanningRoute(mode, target);
                return;
            }
            if (submissionRoute) {
                verifySubmissionRoute(mode, target);
                return;
            }
            if (mode.equals("before")) throw new AssertionError("Old priority unexpectedly accepted the addon overwrite");
            var names = Arrays.stream(transformed.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
            var hooks = selectionList ? List.of("onDrawBackgroundLayer", "wrapStorageAdd", "wrapStorageTooltip", "wrapAdd")
                : List.of("neoecoae$dispatch", "neoecoae$pushWithJob", "neoecoae$releaseWorkerOutput");
            for (String hook : hooks) {
                require(names.stream().anyMatch(name -> name.contains(hook)), "Missing transformed hook: " + hook);
            }
            if (selectionList) {
                verifySelectionListExport(mode, target);
                System.out.println("AE2_ADDON_CPU_LIST_COMPAT PASS mode=" + mode + " transformed=true overlay=true storage=true coprocessors=true tooltip=true");
            } else {
                verifyExport(mode);
                System.out.println("AE2_ADDON_CPU_COMPAT PASS mode=" + mode + " transformed=true jobWrapper=true dispatch=true lifecycle=true");
            }
        } catch (Throwable failure) {
            if (!mode.equals("before") || !isOriginalFailure(failure)) throw failure;
            System.out.println("AE2_ADDON_CPU_COMPAT PASS mode=before reproduced=InvalidInjectionException priority1000_vs_" + sample.get("overwrite_priority").getAsInt());
        }
    }

    private static void restoreOldPlanningEntries(Path overrides) throws Exception {
        try (var input = Ae2AddonCompatibility.class.getClassLoader().getResourceAsStream("neoecoae.mixins.json")) {
            if (input == null) throw new IllegalStateException("Missing production Mixin config");
            var config = JsonParser.parseReader(new InputStreamReader(input,
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            config.addProperty("plugin", "cn.dancingsnow.neoecoae.verification.bootstrap.LegacyPlanningEntryPlugin");
            Files.writeString(overrides.resolve("neoecoae.mixins.json"), config.toString());
        }
    }

    private static void restoreOldSubmissionPoint(Path overrides) throws Exception {
        String resource = "cn/dancingsnow/neoecoae/mixins/ae2/crafting/CraftingServiceEarlyMixin.class";
        var node = productionClass(resource);
        var submit = node.methods.stream().filter(method -> method.name.equals("neoecoae$submit"))
            .findFirst().orElseThrow();
        var annotation = submit.visibleAnnotations.stream()
            .filter(value -> value.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        var points = (List<?>) annotation.values.get(annotation.values.indexOf("at") + 1);
        var point = (org.objectweb.asm.tree.AnnotationNode) points.get(0);
        point.values.set(point.values.indexOf("value") + 1, "INVOKE_ASSIGN");
        writeOverride(overrides, resource, node);
    }

    private static ClassNode productionClass(String resource) throws Exception {
        var node = new ClassNode();
        try (var input = Ae2AddonCompatibility.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing production bytecode: " + resource);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static void writeOverride(Path overrides, String resource, ClassNode node) throws Exception {
        var writer = new ClassWriter(0);
        node.accept(writer);
        Path destination = overrides.resolve(resource);
        Files.createDirectories(destination.getParent());
        Files.write(destination, writer.toByteArray());
    }

    private static ClassNode exportedClass(String target) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class", target.replace('.', '/') + ".class")))
            .accept(node, 0);
        return node;
    }

    private static boolean calls(MethodNode method, String hook) {
        return callIndex(method, hook) >= 0;
    }

    private static int callIndex(MethodNode method, String hook) {
        for (int index = 0; index < method.instructions.size(); index++) {
            if (method.instructions.get(index) instanceof MethodInsnNode call && call.name.contains(hook)) return index;
        }
        return -1;
    }

    private static void verifyPlanningRoute(String mode, String target) throws Exception {
        var node = exportedClass(target);
        var nativeEntries = node.methods.stream().filter(method -> method.name.equals("planJob")
            && (calls(method, "routePlanningRequest") || calls(method, "beginCraftingCalculation"))).toList();
        require(!nativeEntries.isEmpty(), "Missing native calculation entry");
        for (var entry : nativeEntries) {
            require(calls(entry, "routePlanningRequest"), "Native calculation bypassed ECO");
            require(!calls(entry, "beginCraftingCalculation"), "Unwrapped native calculation remains");
        }
        int additionalEntries = 0;
        for (var name : sample.getAsJsonArray("planning_entries")) {
            var entries = node.methods.stream().filter(method -> method.name.contains(name.getAsString())
                && (calls(method, "routePlanningRequest") || calls(method, "beginCraftingCalculation"))).toList();
            require(entries.isEmpty() == mode.equals("base"), "Unexpected addon calculation entries: " + name);
            for (var entry : entries) {
                require(calls(entry, "routePlanningRequest") != mode.equals("before"),
                    "Unexpected addon planning route: " + entry.name + entry.desc);
                require(calls(entry, "beginCraftingCalculation") == mode.equals("before"),
                    "Unexpected original addon calculation call: " + entry.name + entry.desc);
                additionalEntries++;
            }
        }
        System.out.println("AE2_ADDON_PLANNING_ROUTE_COMPAT PASS mode=" + mode + " native=" + nativeEntries.size()
            + " addon=" + additionalEntries + " wrapped=" + !mode.equals("before"));
    }

    private static void verifySubmissionRoute(String mode, String target) throws Exception {
        var node = exportedClass(target);
        var submit = node.methods.stream().filter(method -> method.name.equals("submitJob"))
            .findFirst().orElseThrow();
        int simulation = callIndex(submit, "simulation");
        int nativeScan = callIndex(submit, "findSuitableCraftingCPU");
        int eco = callIndex(submit, "neoecoae$submit");
        int addon = callIndex(submit, sample.get("hook").getAsString());
        require(simulation >= 0 && nativeScan > simulation && eco > simulation,
            "Simulation rejection must precede CPU submission");
        require((addon >= 0) != mode.equals("base"), "Unexpected independent CPU selector");
        int nativeTarget = -1;
        boolean nativeTargetBypassesScan = false;
        for (int index = 0; index < submit.instructions.size(); index++) {
            var instruction = submit.instructions.get(index);
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                    && type.desc.equals("appeng/me/cluster/implementations/CraftingCPUCluster")) nativeTarget = index;
            if (nativeTarget >= 0 && index < Math.min(eco, nativeScan)
                    && instruction instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.GOTO
                    && submit.instructions.indexOf(jump.label) > Math.max(eco, nativeScan)) {
                nativeTargetBypassesScan = true;
            }
        }
        require(nativeTarget > simulation && nativeTargetBypassesScan,
            "Explicit native CPU must retain the direct submission branch");
        if (mode.equals("before")) {
            require(nativeScan < addon && addon < eco, "Did not reproduce competing CPU selection: "
                + nativeScan + "/" + addon + "/" + eco);
        } else {
            require(eco < nativeScan, "ECO selection must run before the automatic native scan");
            require(addon < 0 || nativeScan < addon, "Independent selector must retain its native scan result");
        }
        System.out.println("AE2_ADDON_SUBMISSION_ROUTE_COMPAT PASS mode=" + mode + " simulation=" + simulation
            + " eco=" + eco + " nativeScan=" + nativeScan + " addon=" + addon);
    }

    private static void verifyCraftingStyle(String mode, String target, Class<?> transformed) throws Exception {
        var production = new ClassNode();
        try (var input = Ae2AddonCompatibility.class.getClassLoader().getResourceAsStream(
                "cn/dancingsnow/neoecoae/client/gui/crafting/ECOCraftConfirmScreenIntegration.class")) {
            if (input == null) throw new IllegalStateException("Missing production screen integration");
            new ClassReader(input).accept(production, 0);
        }
        String reportPath = (String) production.fields.stream()
            .filter(field -> field.name.equals("REPORT_STYLE_PATH")).findFirst().orElseThrow().value;
        String requested = mode.equals("before") ? "/screens/eco_craft_confirm.json" : reportPath;
        var exported = new ClassNode();
        String classPath = target.replace('.', '/') + ".class";
        if (mode.equals("base")) {
            try (var input = Launch.classLoader.getResourceAsStream(classPath)) {
                if (input == null) throw new IllegalStateException("Missing native style manager bytecode");
                new ClassReader(input).accept(exported, 0);
            }
        } else new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class", classPath))).accept(exported, 0);
        var load = exported.methods.stream().filter(method -> method.name.equals("loadStyleDoc")).findFirst().orElseThrow();
        String hookName = sample.get("hook").getAsString();
        boolean hooked = false;
        for (var instruction : load.instructions) {
            hooked |= instruction instanceof MethodInsnNode call && call.name.contains(hookName);
        }
        require(hooked == !mode.equals("base"), "Unexpected addon style redirect integration");
        String effective = requested;
        if (hooked) {
            var hook = Arrays.stream(transformed.getDeclaredMethods())
                .filter(method -> method.getName().contains(hookName)).findFirst().orElseThrow();
            hook.setAccessible(true);
            effective = (String) hook.invoke(null, requested);
            require(sample.get("native_redirect").getAsString().equals(hook.invoke(null, sample.get("native_path").getAsString())),
                "Native addon confirmation style must retain its redirect");
        }
        require(mode.equals("before") ? !effective.equals(requested) : effective.equals(requested),
            "Unexpected effective report style: " + effective);
        try (var input = Launch.classLoader.getResourceAsStream("assets/ae2" + effective)) {
            if (input == null) throw new IllegalStateException("Missing effective style resource: " + effective);
            var document = JsonParser.parseReader(new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var images = document.getAsJsonObject("images");
            boolean cycleImages = images != null && images.has("cycleItem") && images.has("cycleItemHovered");
            if (mode.equals("before")) {
                require(!cycleImages, "The original addon style unexpectedly supports the ECO cycle list");
            } else {
                require(cycleImages, "Lost ECO cycle images");
                for (String widget : List.of("verticalToolbar", "scrollbar", "cycleScrollbar", "selectCpu",
                        "start", "cancel", "bookmarkMissing")) {
                    require(document.getAsJsonObject("widgets").has(widget), "Missing ECO report widget: " + widget);
                }
            }
        }
        System.out.println("AE2_ADDON_CRAFTING_STYLE_COMPAT PASS mode=" + mode + " requested=" + requested +
            " effective=" + effective + " cycleImages=" + !mode.equals("before"));
    }

    private static void restoreOldConfirmationIds(Path overrides) throws Exception {
        String resource = "cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftConfirmMenuMixin.class";
        var node = new ClassNode();
        try (var input = Ae2AddonCompatibility.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing confirmation Mixin bytecode");
            new ClassReader(input).accept(node, 0);
        }
        int changed = 0;
        for (var field : node.fields) {
            if (field.visibleAnnotations == null) continue;
            for (var annotation : field.visibleAnnotations) {
                if (!annotation.desc.equals("Lappeng/menu/guisync/GuiSync;")) continue;
                int index = annotation.values.indexOf("value");
                int value = ((Number) annotation.values.get(index + 1)).intValue();
                require(value >= 12000 && value <= 12011, "Unexpected ECO confirmation sync ID: " + value);
                int offset = value - 12000;
                annotation.values.set(index + 1, (short) (offset <= 2 ? offset + 99 : offset + 101));
                changed++;
            }
        }
        require(changed == 12, "Expected all twelve ECO confirmation fields");
        var writer = new ClassWriter(0);
        node.accept(writer);
        Path destination = overrides.resolve(resource);
        Files.createDirectories(destination.getParent());
        Files.write(destination, writer.toByteArray());
    }

    private static void verifyConfirmationSync(String mode, String target, boolean roundTrip) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class", target.replace('.', '/') + ".class"))).accept(node, 0);
        var ids = new HashMap<Integer, String>();
        var duplicates = new ArrayList<Integer>();
        int eco = 0;
        int addon = 0;
        // Reproduce AE2's actual DataSynchronization constructor and packet codec without initializing
        // the menu's static Minecraft/Forge registry. The host has the real transformed sync fields.
        var host = new ClassWriter(0);
        String hostName = "cn/dancingsnow/neoecoae/verification/ConfirmationSyncHost";
        host.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, hostName, null, "java/lang/Object", null);
        for (var field : node.fields) {
            if (field.visibleAnnotations == null) continue;
            for (var annotation : field.visibleAnnotations) {
                if (!annotation.desc.equals("Lappeng/menu/guisync/GuiSync;")) continue;
                int id = ((Number) annotation.values.get(annotation.values.indexOf("value") + 1)).intValue();
                if (ids.putIfAbsent(id, field.name) != null) duplicates.add(id);
                if (field.name.startsWith("neoecoae$")) eco++;
                if (field.name.startsWith(sample.get("field_prefix").getAsString())) addon++;
                var copied = host.visitField(Opcodes.ACC_PUBLIC, field.name, field.desc, null, null);
                annotation.accept(copied.visitAnnotation(annotation.desc, true));
                copied.visitEnd();
            }
        }
        require(eco == 12, "Lost ECO confirmation sync fields: " + eco);
        require((addon > 0) == !mode.equals("base"), "Unexpected addon confirmation integration");
        if (mode.equals("before")) {
            var expected = new ArrayList<Integer>();
            sample.getAsJsonArray("legacy_collisions").forEach(value -> expected.add(value.getAsInt()));
            require(duplicates.containsAll(expected), "Did not reproduce the original collisions: " + duplicates);
        } else require(duplicates.isEmpty(), "Duplicate confirmation sync IDs: " + duplicates);
        var ctor = host.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        host.visitEnd();
        Path hostPath = overrideDirectory.resolve(hostName + ".class");
        Files.createDirectories(hostPath.getParent());
        Files.write(hostPath, host.toByteArray());
        var hostType = Class.forName(hostName.replace('/', '.'), true, Launch.classLoader);
        Object sender = hostType.getConstructor().newInstance();
        var syncType = Class.forName("appeng.menu.guisync.DataSynchronization", true, Launch.classLoader);
        var syncConstructor = syncType.getConstructor(Object.class);
        if (mode.equals("before")) {
            try {
                syncConstructor.newInstance(sender);
                throw new AssertionError("Old confirmation fields unexpectedly accepted");
            } catch (java.lang.reflect.InvocationTargetException error) {
                require(error.getCause() instanceof IllegalStateException &&
                    error.getCause().getMessage().contains("same sync id twice"), "Unexpected original failure: " + error.getCause());
            }
            System.out.println("AE2_ADDON_CRAFT_CONFIRM_COMPAT PASS mode=before reproduced=duplicate_sync_ids_" + duplicates);
            return;
        }
        Object receiver = hostType.getConstructor().newInstance();
        Object senderSync = syncConstructor.newInstance(sender);
        Object receiverSync = syncConstructor.newInstance(receiver);
        // This isolated harness has named Minecraft classes. Release AE2's SRG packet method
        // references need a Forge runtime; exercise its real constructor here, and its codec in
        // the named dependency run and the live client instead.
        if (!roundTrip) {
            System.out.println("AE2_ADDON_CRAFT_CONFIRM_COMPAT PASS mode=" + mode + " fields=" + ids.size() +
                " eco=" + eco + " addon=" + addon + " synchronization=true roundTrip=requires_named_or_live_runtime");
            return;
        }
        for (var field : hostType.getDeclaredFields()) {
            if (field.getType() == String.class) field.set(sender, "");
        }
        var submitError = hostType.getField("submitError");
        submitError.set(sender, submitError.getType().getConstructor(
            Class.forName("appeng.api.networking.crafting.ICraftingSubmitResult", false, Launch.classLoader))
            .newInstance(new Object[] {null}));
        hostType.getField("neoecoae$calculationNanos").setLong(sender, 1234567890123L);
        hostType.getField("neoecoae$planningStatusCode").setInt(sender, 7);
        if (!mode.equals("base")) {
            hostType.getField(sample.get("failure_field").getAsString()).set(sender, "addon.planning.failure");
            hostType.getField(sample.get("availability_field").getAsString()).setBoolean(sender, true);
        }
        Object raw = Class.forName("io.netty.buffer.Unpooled", true, Launch.classLoader).getMethod("buffer").invoke(null);
        Class<?> bufferType = Class.forName("net.minecraft.network.FriendlyByteBuf", true, Launch.classLoader);
        Object buffer = bufferType.getConstructor(Class.forName("io.netty.buffer.ByteBuf", false, Launch.classLoader)).newInstance(raw);
        try {
            syncType.getMethod("writeFull", bufferType).invoke(senderSync, buffer);
            syncType.getMethod("readUpdate", bufferType).invoke(receiverSync, buffer);
            require(hostType.getField("neoecoae$calculationNanos").getLong(receiver) == 1234567890123L, "Lost ECO calculation time");
            require(hostType.getField("neoecoae$planningStatusCode").getInt(receiver) == 7, "Lost ECO planning status");
            if (!mode.equals("base")) {
                require("addon.planning.failure".equals(hostType.getField(sample.get("failure_field").getAsString()).get(receiver)), "Lost addon planning failure");
                require(hostType.getField(sample.get("availability_field").getAsString()).getBoolean(receiver), "Lost addon missing-craft availability");
            }
            bufferType.getMethod("clear").invoke(buffer);
            hostType.getField("neoecoae$planningStatusCode").setInt(sender, 9);
            if (!mode.equals("base")) hostType.getField(sample.get("availability_field").getAsString()).setBoolean(sender, false);
            syncType.getMethod("writeUpdate", bufferType).invoke(senderSync, buffer);
            syncType.getMethod("readUpdate", bufferType).invoke(receiverSync, buffer);
            require(hostType.getField("neoecoae$planningStatusCode").getInt(receiver) == 9, "Lost ECO incremental status update");
            require(hostType.getField("neoecoae$calculationNanos").getLong(receiver) == 1234567890123L, "Incremental update corrupted ECO calculation time");
            if (!mode.equals("base")) {
                require(!hostType.getField(sample.get("availability_field").getAsString()).getBoolean(receiver), "Lost addon incremental missing-craft update");
                require("addon.planning.failure".equals(hostType.getField(sample.get("failure_field").getAsString()).get(receiver)), "Incremental update corrupted addon planning failure");
            }
        } finally {
            bufferType.getMethod("release").invoke(buffer);
        }
        System.out.println("AE2_ADDON_CRAFT_CONFIRM_COMPAT PASS mode=" + mode + " fields=" + ids.size() +
            " eco=" + eco + " addon=" + addon + " synchronization=true roundTrip=true");
    }

    private static void restoreOldPriority(Path overrides) throws Exception {
        var node = new ClassNode();
        try (var input = Ae2AddonCompatibility.class.getClassLoader().getResourceAsStream(MIXIN)) {
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
        boolean overwrittenByAddon = execute.visibleAnnotations != null && execute.visibleAnnotations.stream()
            .anyMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                && annotation.values.contains(addonMixin));
        require(overwrittenByAddon == !mode.equals("base"), "Unexpected owner of the transformed executeCrafting body");
    }

    private static boolean isOriginalFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains("priority 1000 cannot inject into")
                    && message.contains(addonMixin) && message.contains("priority " + sample.get("overwrite_priority").getAsInt())) return true;
        }
        return false;
    }

    private static void verifySelectionListExport(String mode, String target) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class", target.replace('.', '/') + ".class"))).accept(node, 0);
        for (String hook : List.of("onDrawBackgroundLayer", "wrapStorageAdd", "wrapAdd", "wrapStorageTooltip")) {
            String methodName = hook.equals("wrapStorageTooltip") ? "getTooltip" : "drawBackgroundLayer";
            var method = node.methods.stream().filter(value -> value.name.equals(methodName)).findFirst().orElseThrow();
            boolean called = false;
            for (var instruction : method.instructions) {
                called |= instruction instanceof MethodInsnNode call && call.name.contains(hook);
            }
            require(called, "Missing live CPU-list call: " + hook);
        }
        boolean addonSearch = node.interfaces.contains(sample.get("interface").getAsString().replace('.', '/'));
        require(addonSearch == mode.equals("addon"), "Unexpected addon CPU search integration");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
