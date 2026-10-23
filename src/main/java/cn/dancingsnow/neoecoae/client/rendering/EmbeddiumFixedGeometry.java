package cn.dancingsnow.neoecoae.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.function.Function;

/** Optional Embeddium/Xenon public mesh API bridge; no renderer mod is required to load NeoECOAE. */
public final class EmbeddiumFixedGeometry {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddiumFixedGeometry.class);
    private static final String API = "org.embeddedt.embeddium.api.";

    private EmbeddiumFixedGeometry() {}

    public static void initialize() {
        ClassLoader loader = EmbeddiumFixedGeometry.class.getClassLoader();
        Class<? extends Event> eventType;
        try {
            eventType = Class.forName(API + "ChunkMeshEvent", false, loader).asSubclass(Event.class);
        } catch (ClassNotFoundException absent) {
            return;
        }
        try {
            register(MinecraftForge.EVENT_BUS, eventType, Class.forName(API + "MeshAppender", false, loader),
                Class.forName(API + "MeshAppender$Context", false, loader));
            LOG.info("Registered fixed block geometry with the Embeddium/Xenon chunk mesh API");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to register the Embeddium/Xenon chunk mesh API", error);
        }
    }

    // Resolve the optional API once. Its own method names are stable across development and release mappings.
    static void register(IEventBus bus, Class<? extends Event> eventType, Class<?> appenderType,
                         Class<?> contextType) throws ReflectiveOperationException {
        Method getWorld = eventType.getMethod("getWorld");
        Method getOrigin = eventType.getMethod("getSectionOrigin");
        Method addAppender = eventType.getMethod("addMeshAppender", appenderType);
        Method getRegion = contextType.getMethod("blockRenderView");
        Method getBuffers = contextType.getMethod("vertexConsumerProvider");

        bus.addListener(EventPriority.NORMAL, false, eventType, event -> {
            Level world = (Level) invoke(getWorld, event);
            SectionPos section = (SectionPos) invoke(getOrigin, event);
            BlockPos origin = section.origin().immutable();
            // Empty appenders prevent the renderer from skipping empty sections. Only register our sections.
            var positions = world.getChunkAt(origin).getBlockEntities().values().stream()
                .filter(entity -> SectionPos.of(entity.getBlockPos()).equals(section))
                .filter(FixedBlockEntityRenderers::hasRenderer)
                .map(entity -> entity.getBlockPos().immutable()).toList();
            if (positions.isEmpty()) return;

            Object appender = Proxy.newProxyInstance(appenderType.getClassLoader(), new Class<?>[]{appenderType},
                (proxy, method, arguments) -> {
                    return switch (method.getName()) {
                        case "render" -> {
                            Object context = arguments[0];
                            BlockAndTintGetter region = (BlockAndTintGetter) invoke(getRegion, context);
                            @SuppressWarnings("unchecked")
                            var buffers = (Function<RenderType, VertexConsumer>) invoke(getBuffers, context);
                            var entities = new ArrayList<net.minecraft.world.level.block.entity.BlockEntity>(positions.size());
                            // Worker threads read the renderer's section view, never the live client level.
                            for (BlockPos pos : positions) {
                                var entity = region.getBlockEntity(pos);
                                if (FixedBlockEntityRenderers.hasRenderer(entity)) entities.add(entity);
                            }
                            FixedBlockEntityRenderers.renderSection(region, origin, new PoseStack(), buffers::apply, entities);
                            yield null;
                        }
                        case "toString" -> "NeoECOAE fixed geometry at " + origin;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> throw new UnsupportedOperationException(method.toString());
                    };
                });
            invoke(addAppender, event, appender);
        });
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot access the Embeddium/Xenon mesh API", error);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw new IllegalStateException("Embeddium/Xenon mesh API call failed", error.getCause());
        }
    }
}
