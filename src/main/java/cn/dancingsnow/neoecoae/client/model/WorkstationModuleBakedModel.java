package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/** Custom module models use north-facing coordinates, like the built-in workstation models. */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class WorkstationModuleBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<ResourceLocation> MODULE = new ModelProperty<>();
    public WorkstationModuleBakedModel(BakedModel original) { super(original); }

    @SubscribeEvent public static void additionalModels(ModelEvent.RegisterAdditional event) {
        for (var module : ECOWorkstationModules.registered()) {
            if (module.idleModel() != null) event.register(ModelResourceLocation.standalone(module.idleModel()));
            if (module.workingModel() != null) event.register(ModelResourceLocation.standalone(module.workingModel()));
        }
    }
    @SubscribeEvent public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((id, model) -> id.id().equals(ResourceLocation.fromNamespaceAndPath(NeoECOAE.MOD_ID, "integrated_working_station"))
            && id.variant().contains("formed=true") && !(model instanceof WorkstationModuleBakedModel)
            ? new WorkstationModuleBakedModel(model) : model);
    }

    @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        var original = originalModel.getModelData(level, pos, state, data);
        return level.getBlockEntity(pos) instanceof ECOLargeIntegratedWorkingStationBlockEntity host
            ? original.derive().with(MODULE, host.getModuleAppearanceId()).build() : original;
    }

    private BakedModel selected(@Nullable BlockState state, ModelData data) {
        var module = ECOWorkstationModules.find(data.get(MODULE));
        if (module == null) return originalModel;
        boolean working = state != null && state.getOptionalValue(cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation.WORKING).orElse(false);
        ResourceLocation id = working && module.workingModel() != null ? module.workingModel() : module.idleModel();
        if (id == null) return originalModel;
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(id));
        return model instanceof WorkstationModuleBakedModel || model == Minecraft.getInstance().getModelManager().getMissingModel() ? originalModel : model;
    }

    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return selected(state, data).getRenderTypes(state, random, data);
    }

    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                             ModelData data, @Nullable RenderType renderType) {
        BakedModel model = selected(state, data);
        if (model == originalModel) return model.getQuads(state, side, random, data, renderType);
        Direction facing = state == null ? Direction.NORTH : state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
        int turns = switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        Direction localSide = side;
        if (localSide != null && localSide.getAxis().isHorizontal()) for (int i = 0; i < turns; i++) localSide = localSide.getCounterClockWise();
        List<BakedQuad> quads = model.getQuads(state, localSide, random, data, renderType);
        if (turns == 0) return quads;
        List<BakedQuad> rotated = new ArrayList<>(quads.size());
        for (var quad : quads) rotated.add(rotate(quad, turns));
        return rotated;
    }

    static BakedQuad rotate(BakedQuad quad, int turns) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int v = 0; v < 4; v++) {
            int offset = v * stride;
            float x = Float.intBitsToFloat(vertices[offset]), z = Float.intBitsToFloat(vertices[offset + 2]);
            for (int i = 0; i < turns; i++) { float nextX = 1 - z; z = x; x = nextX; }
            vertices[offset] = Float.floatToRawIntBits(x); vertices[offset + 2] = Float.floatToRawIntBits(z);
            if (stride >= 8) {
                int normal = vertices[offset + 7];
                int nx = (byte) normal, nz = (byte) (normal >>> 16);
                for (int i = 0; i < turns; i++) { int nextX = -nz; nz = nx; nx = nextX; }
                vertices[offset + 7] = (normal & 0xFF00FF00) | (nx & 0xFF) | ((nz & 0xFF) << 16);
            }
        }
        Direction direction = quad.getDirection();
        if (direction.getAxis().isHorizontal()) for (int i = 0; i < turns; i++) direction = direction.getClockWise();
        return new BakedQuad(vertices, quad.getTintIndex(), direction, quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }
}
