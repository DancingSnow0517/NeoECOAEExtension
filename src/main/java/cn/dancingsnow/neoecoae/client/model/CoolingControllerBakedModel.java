package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** Keeps the resource-pack model, but emits formed cooling geometry through the section lighting path. */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CoolingControllerBakedModel extends BakedModelWrapper<BakedModel> {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Set<String> CONTROLLERS = Set.of(
        "computation_cooling_controller_l4", "computation_cooling_controller_l6", "computation_cooling_controller_l9");

    public CoolingControllerBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> isFormedController(location)
            && !(model instanceof CoolingControllerBakedModel) ? new CoolingControllerBakedModel(model) : model);
    }

    static boolean isFormedController(ResourceLocation location) {
        return location instanceof ModelResourceLocation model && model.getNamespace().equals(NeoECOAE.MOD_ID)
            && CONTROLLERS.contains(model.getPath())
            && List.of(model.getVariant().split(",")).contains("formed=true");
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return originalModel.getQuads(state, side, random);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        // Breaking overlays and model previews query without a chunk render layer.
        return renderType == null ? originalModel.getQuads(state, side, random, data, null) : List.of();
    }

    public void renderFixed(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        RandomSource random = RandomSource.create(42);
        for (RenderType layer : originalModel.getRenderTypes(state, random, ModelData.EMPTY)) {
            var buffer = buffers.getBuffer(layer);
            boolean ao=originalModel.useAmbientOcclusion(state);
            for (int side = 0; side <= DIRECTIONS.length; side++) {
                random.setSeed(42);
                Direction direction = side == DIRECTIONS.length ? null : DIRECTIONS[side];
                for (BakedQuad quad : originalModel.getQuads(state, direction, random, ModelData.EMPTY, layer)) {
                    BakedQuad rendered = ao ? quad : new BakedQuad(quad.getVertices(), quad.getTintIndex(),
                        quad.getDirection(), quad.getSprite(), quad.isShade(), false);
                    buffer.putBulkData(pose.last(), rendered, 1, 1, 1, 1, light, overlay, true);
                }
            }
        }
    }
}
