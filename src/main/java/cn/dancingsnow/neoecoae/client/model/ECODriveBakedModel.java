package cn.dancingsnow.neoecoae.client.model;

import appeng.client.render.DelegateBakedModel;
import appeng.thirdparty.fabric.RenderContext;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ECODriveBakedModel extends DelegateBakedModel {
    private static final RenderContext.QuadTransform[] FACING_TRANSFORM = new RenderContext.QuadTransform[Direction.values().length];
    private final BakedModel driveFullBase;

    static {
        FACING_TRANSFORM[Direction.NORTH.ordinal()] = quad -> {
            Vector3f pos = new Vector3f();
            for (int i = 0; i < 4; i++) {
                quad.copyPos(i, pos);
                pos.y += 2 / 16f;
                pos.x += 2 / 16f;
                quad.pos(i, pos);
            }
            return false;
        };
        FACING_TRANSFORM[Direction.SOUTH.ordinal()] = quad -> {
            Vector3f pos = new Vector3f();
            for (int i = 0; i < 4; i++) {
                quad.copyPos(i, pos);
                pos.y += 2 / 16f;
                pos.x += 14 / 16f;
                pos.z += 16 / 16f;
                quad.pos(i, pos);
            }
            return false;
        };
        FACING_TRANSFORM[Direction.EAST.ordinal()] = quad -> {
            Vector3f pos = new Vector3f();
            for (int i = 0; i < 4; i++) {
                quad.copyPos(i, pos);
                pos.y += 2 / 16f;
                pos.x += 16 / 16f;
                pos.z += 2 / 16f;
                quad.pos(i, pos);
            }
            return false;
        };
        FACING_TRANSFORM[Direction.WEST.ordinal()] = quad -> {
            Vector3f pos = new Vector3f();
            for (int i = 0; i < 4; i++) {
                quad.copyPos(i, pos);
                pos.y += 2 / 16f;
                pos.z += 14 / 16f;
                quad.pos(i, pos);
            }
            return false;
        };
    }

    protected ECODriveBakedModel(
        BakedModel base,
        BakedModel driveFullBase
    ) {
        super(base);
        this.driveFullBase = driveFullBase;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutout());
    }

    @Override
    public List<BakedQuad> getQuads(
        @Nullable BlockState state,
        @Nullable Direction side,
        RandomSource rand,
        ModelData data,
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
        return quads;
    }
}