package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** Keeps the resource-pack model, but emits formed cooling geometry through the section lighting path. */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class CoolingControllerBakedModel extends BakedModelWrapper<BakedModel> {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Set<String> CONTROLLERS = Set.of(
        "computation_cooling_controller_l4", "computation_cooling_controller_l6", "computation_cooling_controller_l9");
    private static final ResourceLocation GUIDEBOOK_LEVEL = ResourceLocation.fromNamespaceAndPath("guideme", "guidebook");
    private static final ModelProperty<Boolean> GUIDEBOOK_PREVIEW = new ModelProperty<>();

    public CoolingControllerBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> isFormedController(location)
            && !(model instanceof CoolingControllerBakedModel) ? new CoolingControllerBakedModel(model) : model);
    }

    static boolean isFormedController(ModelResourceLocation location) {
        return location.id().getNamespace().equals(NeoECOAE.MOD_ID)
            && CONTROLLERS.contains(location.id().getPath())
            && List.of(location.variant().split(",")).contains("formed=true");
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return originalModel.getQuads(state, side, random);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        var original = originalModel.getModelData(level, pos, state, data);
        if (level instanceof Level scene && GUIDEBOOK_LEVEL.equals(scene.dimension().location())) {
            return original.derive().with(GUIDEBOOK_PREVIEW, true).build();
        }
        return original;
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        return data.has(GUIDEBOOK_PREVIEW) ? TriState.FALSE : super.useAmbientOcclusion(state, data, renderType);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        return renderType == null || data.has(GUIDEBOOK_PREVIEW)
            ? originalModel.getQuads(state, side, random, data, renderType) : List.of();
    }

    public void renderFixed(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        RandomSource random = RandomSource.create(42);
        for (RenderType layer : originalModel.getRenderTypes(state, random, ModelData.EMPTY)) {
            var buffer = buffers.getBuffer(layer);
            var setting = originalModel.useAmbientOcclusion(state, ModelData.EMPTY, layer);
            boolean ao = setting.isTrue() || setting.isDefault() && originalModel.useAmbientOcclusion();
            for (int side = 0; side <= DIRECTIONS.length; side++) {
                random.setSeed(42);
                Direction direction = side == DIRECTIONS.length ? null : DIRECTIONS[side];
                for (BakedQuad quad : originalModel.getQuads(state, direction, random, ModelData.EMPTY, layer)) {
                    BakedQuad rendered = ao ? quad : new BakedQuad(quad.getVertices(), quad.getTintIndex(),
                        quad.getDirection(), quad.getSprite(), quad.isShade(), false);
                    buffer.putBulkData(pose.last(), rendered, 1, 1, 1, 1, light, overlay);
                }
            }
        }
    }
}
