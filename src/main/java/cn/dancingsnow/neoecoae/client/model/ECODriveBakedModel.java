package cn.dancingsnow.neoecoae.client.model;

import appeng.client.render.DelegateBakedModel;
import appeng.thirdparty.fabric.MutableQuadView;
import appeng.thirdparty.fabric.RenderContext;
import cn.dancingsnow.neoecoae.blocks.ECODriveBlock;
import cn.dancingsnow.neoecoae.client.model.data.ECODriveModelData;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ECODriveBakedModel extends DelegateBakedModel {

    private final Map<Item, BakedModel> cellModels;
    private final BakedModel defaultModel;
    private final BakedModel driveFullBase;
    private final Transformation modelTransform;
    private final RenderContext.QuadTransform transform;

    protected ECODriveBakedModel(
        BakedModel base,
        BakedModel driveFullBase,
        Map<Item, BakedModel> cellModels,
        BakedModel defaultModel,
        Transformation modelTransform
    ) {
        super(base);
        this.cellModels = cellModels;
        this.defaultModel = defaultModel;
        this.driveFullBase = driveFullBase;
        this.modelTransform = modelTransform;
        this.transform = createTransform(modelTransform);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutout());
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(
        @Nullable BlockState state,
        @Nullable Direction side,
        @NotNull RandomSource rand,
        @NotNull ModelData data,
        @Nullable RenderType renderType
    ) {
        if (state == null) {
            return super.getQuads(null, side, rand, data, renderType);
        }
        List<BakedQuad> quads = new ArrayList<>();
        if (state.getValue(ECODriveBlock.HAS_CELL)) {
            quads.addAll(driveFullBase.getQuads(state, side, rand, data, renderType));
        } else {
            quads.addAll(this.getBaseModel().getQuads(state, side, rand, data, renderType));
        }
        ItemStack cellStack = data.get(ECODriveModelData.CELL);
        if (cellStack != null && !cellStack.isEmpty()) {
            BakedModel model = cellModels.getOrDefault(cellStack.getItem(), defaultModel);
            MutableQuadView quadView = MutableQuadView.getInstance();
            for (BakedQuad quad : model.getQuads(state, side, rand, data, renderType)) {
                quadView.fromVanilla(quad, side);
                transform.transform(quadView);
                quads.add(quadView.toBlockBakedQuad());
            }
        }
        return quads;
    }

    private RenderContext.QuadTransform createTransform(Transformation transformation) {
        return quad -> {
            Vector3f pos = new Vector3f();
            for (int i = 0; i < 4; i++) {
                quad.copyPos(i, pos);
                transformation.getLeftRotation().transform(pos);
                quad.pos(i, pos);
            }
            return false;
        };
    }
}