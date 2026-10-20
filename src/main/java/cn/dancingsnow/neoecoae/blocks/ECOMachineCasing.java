package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.client.ClassicPackDetector;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public class ECOMachineCasing<C extends NECluster<C>> extends NEBlock<ECOMachineCasingBlockEntity<C>> {

    public static final BooleanProperty INVISIBLE = BooleanProperty.create("invisible");
    public static final BooleanProperty CLASSIC_VISIBLE = BooleanProperty.create("classic_visible");

    public ECOMachineCasing(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
            .setValue(FORMED, false)
            .setValue(INVISIBLE, false)
            .setValue(CLASSIC_VISIBLE, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(INVISIBLE, CLASSIC_VISIBLE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        if (isEffectivelyInvisible(state)) {
            return RenderShape.INVISIBLE;
        }
        return RenderShape.MODEL;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return isEffectivelyInvisible(state);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return isEffectivelyInvisible(state) ? 1 : 0.2f;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isEffectivelyInvisible(state) ? Shapes.empty() : super.getVisualShape(state, level, pos, context);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return isEffectivelyInvisible(state);
    }

    private boolean isEffectivelyInvisible(BlockState state) {
        return state.getValue(INVISIBLE) && !shouldRenderClassicStorageCasing(state);
    }

    private boolean shouldRenderClassicStorageCasing(BlockState state) {
        return FMLEnvironment.dist == Dist.CLIENT
            && BuiltInRegistries.BLOCK.getKey(this).equals(NeoECOAE.id("storage_casing"))
            && state.getValue(CLASSIC_VISIBLE)
            && ClassicPackDetector.isActive();
    }
}
