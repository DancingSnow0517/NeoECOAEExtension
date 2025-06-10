package cn.dancingsnow.neoecoae.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldSceneRenderer.class)
public class WorldSceneRendererMixin {
    @Shadow @Final public Level world;

    @WrapOperation(
        method = "renderBlocks",
        at = @At(
            value = "INVOKE",
            target = "Lcom/lowdragmc/lowdraglib/client/scene/WorldSceneRenderer;canRenderInLayer(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/RenderType;)Z"
        )
    )
    private boolean workaround(
        BlockState state,
        RenderType renderType,
        Operation<Boolean> original,
        @Local BlockEntity blockEntity
    ) {
        ModelData modelData = blockEntity == null ? ModelData.EMPTY : blockEntity.getModelData();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        ChunkRenderTypeSet set = model.getRenderTypes(state, this.world.random, modelData);
        return set.contains(renderType);
    }
}
