import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/** Read-only inspection scheduled on the existing Minecraft client's main thread. */
public final class RenderDiagnosticAgent {
    private static final List<String> OUTPUT = new ArrayList<>();
    private static ClassLoader gameLoader;

    public static void agentmain(String outputPath, Instrumentation instrumentation) {
        try {
            OUTPUT.clear();
            Class<?> minecraft = null;
            for (Class<?> candidate : instrumentation.getAllLoadedClasses()) {
                if (candidate.getName().equals("net.minecraft.client.Minecraft")) minecraft = candidate;
            }
            if (minecraft == null) throw new IllegalStateException("Minecraft class is not loaded");
            gameLoader = minecraft.getClassLoader();
            Object client = method(minecraft, new String[]{"getInstance", "m_91087_"}).invoke(null);
            ((Executor) client).execute(() -> {
                try {
                    inspect(client);
                } catch (Throwable failure) {
                    StringWriter trace = new StringWriter();
                    failure.printStackTrace(new PrintWriter(trace));
                    OUTPUT.add(trace.toString());
                } finally {
                    try { Files.write(Path.of(outputPath), OUTPUT, StandardCharsets.UTF_8); }
                    catch (Exception failure) { failure.printStackTrace(); }
                }
            });
        } catch (Throwable failure) {
            try { Files.writeString(Path.of(outputPath), failure.toString(), StandardCharsets.UTF_8); }
            catch (Exception ignored) { failure.printStackTrace(); }
        }
    }

    private static void inspect(Object client) throws Exception {
        Object level = field(client.getClass(), "level", "f_91073_").get(client);
        Object hit = field(client.getClass(), "hitResult", "f_91077_").get(client);
        if (level == null || hit == null) throw new IllegalStateException("Client has no loaded world or target");
        Object origin = method(hit.getClass(), new String[]{"getBlockPos", "m_82425_"}).invoke(hit);
        Class<?> positionType = type("net.minecraft.core.BlockPos");
        int x = (int) method(positionType, new String[]{"getX", "m_123341_"}).invoke(origin);
        int y = (int) method(positionType, new String[]{"getY", "m_123342_"}).invoke(origin);
        int z = (int) method(positionType, new String[]{"getZ", "m_123343_"}).invoke(origin);
        Object renderer = method(client.getClass(), new String[]{"getBlockRenderer", "m_91289_"}).invoke(client);
        Method stateAt = method(level.getClass(), new String[]{"getBlockState", "m_8055_"}, positionType);
        Method entityAt = method(level.getClass(), new String[]{"getBlockEntity", "m_7702_"}, positionType);
        OUTPUT.add("target=" + origin + " state=" + stateAt.invoke(level, origin));
        Class<?> fixed = type("cn.dancingsnow.neoecoae.client.rendering.FixedBlockEntityRenderers");
        Map<?, ?> registered = (Map<?, ?>) field(fixed, "renderers").get(null);
        OUTPUT.add("fixedRendererTypes=" + registered.size());
        for (int dx = -16; dx <= 16; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -16; dz <= 16; dz++) {
            Object position = positionType.getConstructor(int.class, int.class, int.class).newInstance(x + dx, y + dy, z + dz);
            Object state = stateAt.invoke(level, position);
            if (!(position.equals(origin) || state.toString().toLowerCase(java.util.Locale.ROOT).contains("cooling"))) continue;
            Object entity = entityAt.invoke(level, position);
            OUTPUT.add("block=" + position + " state=" + state + " entity=" + (entity == null ? "null" : entity.getClass().getName()));
            if (entity != null) {
                Object entityType = method(entity.getClass(), new String[]{"getType", "m_58903_"}).invoke(entity);
                OUTPUT.add("fixedRenderer=" + registered.get(entityType));
                OUTPUT.add("entityState=" + method(entity.getClass(), new String[]{"getBlockState", "m_58900_"}).invoke(entity));
            }
            Object model = method(renderer.getClass(), new String[]{"getBlockModel", "m_110910_"},
                type("net.minecraft.world.level.block.state.BlockState")).invoke(renderer, state);
            for (int depth = 0; depth < 4; depth++) {
                dumpModel(state, model);
                try { model = field(model.getClass(), "originalModel", "base").get(model); }
                catch (NoSuchFieldException end) { break; }
            }
        }
    }

    private static void dumpModel(Object state, Object model) throws Exception {
        OUTPUT.add("model=" + model.getClass().getName());
        Class<?> randomType = type("net.minecraft.util.RandomSource");
        Object random = method(randomType, new String[]{"create", "m_216335_"}, long.class).invoke(null, 42L);
        Object data = field(type("net.minecraftforge.client.model.data.ModelData"), "EMPTY").get(null);
        Object layers = method(type("net.minecraft.client.resources.model.BakedModel"), new String[]{"getRenderTypes"},
            type("net.minecraft.world.level.block.state.BlockState"), randomType, data.getClass()).invoke(model, state, random, data);
        Method quads = method(type("net.minecraft.client.resources.model.BakedModel"), new String[]{"getQuads"},
            type("net.minecraft.world.level.block.state.BlockState"), type("net.minecraft.core.Direction"),
            randomType, data.getClass(), type("net.minecraft.client.renderer.RenderType"));
        OUTPUT.add("model=" + model.getClass().getName() + " previewQuads="
            + ((List<?>) quads.invoke(model, state, null, random, data, null)).size());
        for (Object layer : (Iterable<?>) layers) {
            OUTPUT.add("layer=" + layer + " quads=" + ((List<?>) quads.invoke(model, state, null, random, data, layer)).size());
        }
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, gameLoader);
    }

    private static Method method(Class<?> owner, String[] names, Class<?>... parameters) throws NoSuchMethodException {
        for (String name : names) {
            try { return owner.getMethod(name, parameters); }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(owner.getName() + "." + String.join("/", names));
    }

    private static Field field(Class<?> owner, String... names) throws NoSuchFieldException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) for (String name : names) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(owner.getName() + "." + String.join("/", names));
    }
}
