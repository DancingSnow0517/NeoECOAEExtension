package cn.dancingsnow.neoecoae.client.rendering;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class EmbeddiumFixedGeometryTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void replacementChunkCompilerEmitsFixedGeometryThroughItsOwnBuffers() throws Exception {
        IEventBus bus = bus();
        SectionPos section = SectionPos.of(-2, 11, 3);
        BlockPos origin = section.origin();
        BlockPos pos = origin.offset(3, 5, 9);
        BlockEntity entity = entity(pos);
        AtomicInteger rendered = new AtomicInteger();
        FixedBlockEntityRenderers.register(entity.getType(), (blockEntity, partialTick, pose, buffers, light, overlay) -> {
            assertSame(entity, blockEntity);
            assertEquals(3, pose.last().pose().m30(), 0.0001F);
            assertEquals(5, pose.last().pose().m31(), 0.0001F);
            assertEquals(9, pose.last().pose().m32(), 0.0001F);
            buffers.getBuffer(RenderType.cutout()).vertex(1, 2, 3);
            rendered.incrementAndGet();
        });

        Level world = world(origin, Map.of(pos, entity));
        MeshEvent event = new MeshEvent(world, section);
        bus.post(event);
        assertEquals(1, event.appenders.size());
        // The appender must use the section snapshot even if the full client world has moved on.
        clearInvocations(world);
        BlockAndTintGetter region = region();
        when(region.getBlockEntity(pos)).thenReturn(entity);
        VertexConsumer vertices = mock(VertexConsumer.class);

        try (var minecraft = mockStatic(Minecraft.class)) {
            Minecraft client = mock(Minecraft.class);
            when(client.getBlockColors()).thenReturn(mock(BlockColors.class));
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            event.appenders.get(0).render(new MeshContext(layer -> {
                assertSame(RenderType.cutout(), layer);
                return vertices;
            }, region));
        }

        assertEquals(1, rendered.get());
        verify(vertices).vertex(1, 2, 3);
        verify(region).getBlockEntity(pos);
        verifyNoInteractions(world);
    }

    @Test
    void emptyUnregisteredAndNeighborSectionsDoNotCreateAppenders() throws Exception {
        IEventBus bus = bus();
        SectionPos section = SectionPos.of(2, -1, -3);
        BlockPos origin = section.origin();
        BlockEntity neighbor = entity(origin.above(16));
        FixedBlockEntityRenderers.register(neighbor.getType(), (entity, tick, pose, buffers, light, overlay) -> fail());
        BlockEntity ordinary = entity(origin.offset(1, 2, 3));
        MeshEvent empty = new MeshEvent(world(origin, Map.of()), section);
        MeshEvent unrelated = new MeshEvent(world(origin,
            Map.of(neighbor.getBlockPos(), neighbor, ordinary.getBlockPos(), ordinary)), section);

        bus.post(empty);
        bus.post(unrelated);

        assertTrue(empty.appenders.isEmpty(), "Empty appenders disable the renderer's empty-section optimization");
        assertTrue(unrelated.appenders.isEmpty());
    }

    @Test
    void removedBlockEntitiesAreSkippedWhenTheWorkerStarts() throws Exception {
        IEventBus bus = bus();
        SectionPos section = SectionPos.of(0, 0, 0);
        BlockPos pos = section.origin().offset(1, 2, 3);
        BlockEntity entity = entity(pos);
        FixedBlockEntityRenderers.register(entity.getType(), (blockEntity, tick, pose, buffers, light, overlay) -> fail());
        MeshEvent event = new MeshEvent(world(section.origin(), Map.of(pos, entity)), section);
        bus.post(event);

        event.appenders.get(0).render(new MeshContext(layer -> {
            fail("A removed entity must not allocate buffers");
            return null;
        }, region()));
    }

    @Test
    void optionalRendererIsNotRequiredOnTheNormalClientClasspath() {
        assertDoesNotThrow(EmbeddiumFixedGeometry::initialize);
    }

    private static IEventBus bus() throws Exception {
        IEventBus bus = BusBuilder.builder().build();
        bus.start();
        EmbeddiumFixedGeometry.register(bus, MeshEvent.class, MeshAppender.class, MeshContext.class);
        return bus;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockEntity entity(BlockPos pos) {
        BlockEntity entity = mock(BlockEntity.class);
        BlockEntityType type = mock(BlockEntityType.class);
        when(entity.getType()).thenReturn(type);
        when(entity.getBlockPos()).thenReturn(pos);
        when(entity.getBlockState()).thenReturn(Blocks.STONE.defaultBlockState());
        return entity;
    }

    private static Level world(BlockPos origin, Map<BlockPos, BlockEntity> entities) {
        Level world = mock(Level.class);
        LevelChunk chunk = mock(LevelChunk.class);
        when(world.getChunkAt(origin)).thenReturn(chunk);
        when(chunk.getBlockEntities()).thenReturn(entities);
        return world;
    }

    private static BlockAndTintGetter region() {
        BlockAndTintGetter region = mock(BlockAndTintGetter.class);
        when(region.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        when(region.getBrightness(any(), any())).thenReturn(15);
        return region;
    }

    public static final class MeshEvent extends Event {
        private final Level world;
        private final SectionPos section;
        final ArrayList<MeshAppender> appenders = new ArrayList<>();

        public MeshEvent() {
            this(null, null);
        }

        public MeshEvent(Level world, SectionPos section) {
            this.world = world;
            this.section = section;
        }

        public Level getWorld() { return world; }
        public SectionPos getSectionOrigin() { return section; }
        public void addMeshAppender(MeshAppender appender) { appenders.add(appender); }
    }

    public interface MeshAppender {
        void render(MeshContext context);
    }

    public record MeshContext(Function<RenderType, VertexConsumer> vertexConsumerProvider,
                              BlockAndTintGetter blockRenderView) {}
}
