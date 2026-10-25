package cn.dancingsnow.neoecoae.compat.ae2;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuForceStartMixin;
import cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Checks the real release bytecode; set NEOECOAE_COSMOLITE_JAR to the JAR embedded in MoniLabs. */
class AE2CosmoliteCompatibilityTest {
    private static final String MIXINS = "cn/dancingsnow/neoecoae/mixins/";

    @TestFactory
    Stream<DynamicTest> requiredHooksMatchStandardAe2() throws Exception {
        return checkHooks(null);
    }

    @TestFactory
    Stream<DynamicTest> requiredHooksMatchActualCosmolite() throws Exception {
        String path = System.getenv("NEOECOAE_COSMOLITE_JAR");
        assumeTrue(path != null && !path.isBlank(), "Supply the actual embedded Cosmolite JAR");
        try (var jar = new JarFile(Path.of(path).toFile())) {
            return checkHooks(jar);
        }
    }

    private Stream<DynamicTest> checkHooks(JarFile jar) throws Exception {
        var tests = new ArrayList<DynamicTest>();
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/neoecoae.mixins.json"),
                StandardCharsets.UTF_8)) {
            var config = JsonParser.parseReader(reader).getAsJsonObject();
            for (String section : List.of("mixins", "client")) {
                for (var entry : config.getAsJsonArray(section)) {
                    var mixin = read(null, MIXINS + entry.getAsString().replace('.', '/'));
                    var annotation = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                            .filter(value -> value.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
                    var targets = (List<?>) value(annotation, "value");
                    if (targets == null) continue;
                    for (var type : targets) {
                        String name = ((Type) type).getInternalName();
                        if (!name.startsWith("appeng/")) continue;
                        var target = read(jar, name);
                        for (var handler : mixin.methods) {
                            for (var injection : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
                                if (value(injection, "method") == null || Integer.valueOf(0).equals(value(injection, "require")))
                                    continue;
                                // The supplied release uses SRG names for these Minecraft overrides.
                                if (jar != null && Boolean.TRUE.equals(value(injection, "remap"))) continue;
                                List<MethodNode> methods = selectedMethods(target, injection);
                                tests.add(DynamicTest.dynamicTest(entry.getAsString() + "." + handler.name,
                                        () -> verifyHook(target, handler, injection, methods)));
                            }
                        }
                        if (entry.getAsString().endsWith("CraftConfirmMenuMixin")) {
                            tests.add(DynamicTest.dynamicTest("exactly one confirmation submission wrapper matches", () -> {
                                long count = mixin.methods.stream().flatMap(handler ->
                                        annotations(handler.visibleAnnotations, handler.invisibleAnnotations).stream())
                                        .filter(injection -> injection.desc.endsWith("/WrapOperation;")
                                                && selectors(injection).anyMatch(selector -> selector.startsWith("startJob")))
                                        .mapToLong(injection -> selectedMethods(target, injection).stream()
                                                .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                                                .filter(instruction -> instruction instanceof MethodInsnNode call
                                                        && ((List<?>) value(injection, "at")).stream()
                                                        .anyMatch(at -> ("L" + call.owner + ";" + call.name + call.desc)
                                                                .equals(value((AnnotationNode) at, "target")))).count()).sum();
                                assertEquals(1, count);
                            }));
                        }
                        if (entry.getAsString().endsWith("CraftingPlanSummaryEntryAccessor")) {
                            tests.add(DynamicTest.dynamicTest("summary quantities retain the runtime entry layout", () -> {
                                for (var setter : mixin.methods) {
                                    var accessor = annotations(setter.visibleAnnotations, setter.invisibleAnnotations)
                                            .stream().filter(value -> value.desc.endsWith("/Accessor;")).findFirst().orElseThrow();
                                    assertTrue(target.fields.stream().anyMatch(field -> field.name.equals(value(accessor, "value"))
                                            && field.desc.equals("J")));
                                }
                            }));
                        }
                    }
                }
            }
        }
        // Materialize the class nodes while the optional release JAR is open.
        return tests.stream();
    }

    private void verifyHook(ClassNode target, MethodNode handler, AnnotationNode injection,
            List<MethodNode> methods) {
        String context = target.name + ": " + handler.name;
        assertFalse(methods.isEmpty(), context + " has no target method");
        if (injection.desc.endsWith("/WrapMethod;")) {
            var arguments = Type.getArgumentTypes(handler.desc);
            var wrapped = Arrays.copyOf(arguments, arguments.length - 1);
            for (var method : methods) assertArrayEquals(Type.getArgumentTypes(method.desc), wrapped, context);
        }
        if (injection.desc.endsWith("/Inject;")) {
            var arguments = Type.getArgumentTypes(handler.desc);
            int callback = 0;
            while (callback < arguments.length && !arguments[callback].getDescriptor()
                    .startsWith("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo")) callback++;
            if (callback > 0 && callback < arguments.length) {
                var captured = Arrays.copyOf(arguments, callback);
                for (var method : methods) {
                    var expected = Type.getArgumentTypes(method.desc);
                    assertEquals(expected.length, captured.length, context);
                    for (int index = 0; index < expected.length; index++) {
                        var parameter = handler.invisibleParameterAnnotations == null
                                ? null : handler.invisibleParameterAnnotations[index];
                        if (parameter != null && parameter.stream().anyMatch(value -> value.desc.endsWith("/Coerce;"))) continue;
                        assertEquals(expected[index], captured[index], context);
                    }
                }
            }
        }
        Object points = value(injection, "at");
        if (points == null) return;
        for (var at : points instanceof List<?> list ? list : List.of(points)) {
            var point = (AnnotationNode) at;
            if (Boolean.TRUE.equals(value(point, "remap"))) continue;
            String member = (String) value(point, "target");
            if (member == null || member.isEmpty()) continue;
            long count = methods.stream().flatMap(method -> Stream.of(method.instructions.toArray()))
                    .filter(instruction -> instruction instanceof MethodInsnNode call
                            && member.equals("L" + call.owner + ";" + call.name + call.desc)
                            || instruction instanceof FieldInsnNode field
                            && member.equals("L" + field.owner + ";" + field.name + ":" + field.desc)
                            || instruction instanceof TypeInsnNode type && "NEW".equals(value(point, "value"))
                            && member.equals(type.desc)).count();
            int ordinal = value(point, "ordinal") instanceof Integer selected ? selected : -1;
            assertTrue(count > Math.max(ordinal, 0), context + " cannot reach " + member);
        }
    }

    @Test
    void summaryRefreshDoesNotInvokeAForkSpecificConstructor() throws Exception {
        var mixin = read(null, MIXINS + "ae2/menu/CraftConfirmMenuMixin");
        var refresh = mixin.methods.stream().filter(method -> method.name.equals("neoecoae$recheckStoredAmounts"))
                .findFirst().orElseThrow();
        assertTrue(Arrays.stream(refresh.instructions.toArray()).noneMatch(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.equals("appeng/menu/me/crafting/CraftingPlanSummaryEntry") && call.name.equals("<init>")));
    }

    @Test
    void optionalFollowingWrapperKeepsItsDescriptorDuringPermissiveMatching() throws Exception {
        var method = CraftConfirmMenuForceStartMixin.class.getDeclaredMethod("neoecoae$restoreFollowingPlan",
                boolean.class, Operation.class);
        var annotation = method.getAnnotation(com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod.class);
        var selector = org.spongepowered.asm.mixin.injection.selectors.MemberMatcher.parse(annotation.method()[0], null)
                .validate().configure(org.spongepowered.asm.mixin.injection.selectors.ITargetSelector.Configure.PERMISSIVE);
        var owner = new ClassNode();
        for (String descriptor : List.of("()V", "(Z)V")) {
            var candidate = new MethodNode(0, "startJob", descriptor, null, null);
            assertEquals(descriptor.equals("(Z)V"), selector.match(
                    org.spongepowered.asm.mixin.injection.selectors.ElementNode.of(owner, candidate)).isExactMatch());
        }
    }

    @Test
    void directAe2CallsRetainTheSharedReleaseApi() throws Exception {
        String path = System.getenv("NEOECOAE_COSMOLITE_JAR");
        assumeTrue(path != null && !path.isBlank(), "Supply the actual embedded Cosmolite JAR");
        var classes = Path.of(CraftConfirmMenuMixin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var missing = new java.util.TreeSet<String>();
        var minecraftNames = new java.util.HashMap<String, java.util.Set<String>>();
        for (String line : Files.readAllLines(Path.of("build/moddev/artifacts/namedToIntermediate.tsrg"))) {
            if (!line.startsWith("\t")) continue;
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 3 && parts[1].startsWith("(")) {
                minecraftNames.computeIfAbsent(parts[0] + parts[1], ignored -> new java.util.HashSet<>()).add(parts[2]);
            }
        }
        try (var jar = new JarFile(Path.of(path).toFile()); var files = Files.walk(classes)) {
            for (var file : files.filter(value -> value.toString().endsWith(".class")).toList()) {
                var node = new ClassNode();
                new ClassReader(Files.readAllBytes(file)).accept(node, 0);
                for (var method : node.methods) {
                    for (var instruction : method.instructions) {
                        if (!(instruction instanceof MethodInsnNode call) || !call.owner.startsWith("appeng/")) continue;
                        var standard = read(null, call.owner);
                        // Minecraft overrides are remapped separately, and inherited members resolve through their owner.
                        if (standard.methods.stream().noneMatch(candidate -> candidate.name.equals(call.name)
                                && candidate.desc.equals(call.desc))) continue;
                        boolean exists = hasMethod(jar, call.owner, call.name, call.desc);
                        for (var mapped : minecraftNames.getOrDefault(call.name + call.desc, java.util.Set.of())) {
                            exists |= hasMethod(jar, call.owner, mapped, call.desc);
                        }
                        if (!exists) {
                            missing.add(node.name + "." + method.name + " -> " + call.owner + "." + call.name + call.desc);
                        }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), String.join("\n", missing));
    }

    private boolean hasMethod(JarFile jar, String owner, String name, String descriptor) throws Exception {
        if (!owner.startsWith("appeng/") || jar.getJarEntry(owner + ".class") == null) return false;
        var node = read(jar, owner);
        if (node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor))) return true;
        if (name.equals("<init>")) return false;
        for (var contract : node.interfaces) if (hasMethod(jar, contract, name, descriptor)) return true;
        return node.superName != null && hasMethod(jar, node.superName, name, descriptor);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void followingSubmissionPreservesTheNativeArguments(boolean following) throws Exception {
        var menu = new CraftConfirmMenuMixin();
        var service = mock(ICraftingService.class);
        var plan = mock(ICraftingPlan.class);
        var requester = mock(ICraftingRequester.class);
        var cpu = mock(ICraftingCPU.class);
        var source = mock(IActionSource.class);
        var expected = CraftingSubmitResult.successful(null);
        var invocations = new ArrayList<Object[]>();
        Operation<ICraftingSubmitResult> original = args -> { invocations.add(args); return expected; };
        var method = CraftConfirmMenuMixin.class.getDeclaredMethod("submitConfirmedFollowingCyclePlan",
                ICraftingService.class, ICraftingPlan.class, ICraftingRequester.class, ICraftingCPU.class,
                boolean.class, IActionSource.class, boolean.class, Operation.class);
        method.setAccessible(true);
        assertSame(expected, method.invoke(menu, service, plan, requester, cpu, true, source, following, original));
        assertEquals(1, invocations.size());
        assertArrayEquals(new Object[]{service, plan, requester, cpu, true, source, following}, invocations.get(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void followingForceStartRestoresThePlanEvenOnFailure(boolean fails) throws Exception {
        var menu = new CraftConfirmMenuForceStartMixin() {};
        var originalPlan = mock(ICraftingPlan.class);
        set(menu, "result", mock(ICraftingPlan.class));
        set(menu, "neoecoae$originalSimulationResult", originalPlan);
        set(menu, "neoecoae$forceCraftStartActive", true);
        var failure = new IllegalStateException("submission failed");
        Operation<Void> original = args -> {
            assertArrayEquals(new Object[]{true}, args);
            if (fails) throw failure;
            return null;
        };
        var method = CraftConfirmMenuForceStartMixin.class.getDeclaredMethod("neoecoae$restoreFollowingPlan",
                boolean.class, Operation.class);
        method.setAccessible(true);
        if (fails) assertSame(failure, assertThrows(InvocationTargetException.class,
                () -> method.invoke(menu, true, original)).getCause());
        else method.invoke(menu, true, original);
        assertFalse(menu.neoecoae$isForceCraftStartActive());
        var result = CraftConfirmMenuForceStartMixin.class.getDeclaredField("result");
        result.setAccessible(true);
        assertSame(originalPlan, result.get(menu));
    }

    private static void set(Object menu, String name, Object value) throws Exception {
        var field = CraftConfirmMenuForceStartMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(menu, value);
    }

    private static Stream<String> selectors(AnnotationNode annotation) {
        Object methods = value(annotation, "method");
        return methods instanceof List<?> list ? list.stream().map(Object::toString) : Stream.of(methods.toString());
    }

    private static List<MethodNode> selectedMethods(ClassNode owner, AnnotationNode injection) {
        var methods = new java.util.LinkedHashSet<MethodNode>();
        for (String text : selectors(injection).toList()) {
            var selector = org.spongepowered.asm.mixin.injection.selectors.TargetSelector.parse(text, null)
                    .configure(org.spongepowered.asm.mixin.injection.selectors.ITargetSelector.Configure.SELECT_MEMBER);
            owner.methods.stream().filter(method -> selector.match(
                    org.spongepowered.asm.mixin.injection.selectors.ElementNode.of(owner, method)).isExactMatch())
                    .limit(selector.getMaxMatchCount()).forEach(methods::add);
        }
        return new ArrayList<>(methods);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        int index = annotation.values.indexOf(key);
        return index < 0 ? null : annotation.values.get(index + 1);
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        var values = new ArrayList<AnnotationNode>();
        if (visible != null) values.addAll(visible);
        if (invisible != null) values.addAll(invisible);
        return values;
    }

    private ClassNode read(JarFile jar, String name) throws Exception {
        try (var stream = jar == null ? getClass().getResourceAsStream("/" + name + ".class")
                : jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            assertNotNull(stream, name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
