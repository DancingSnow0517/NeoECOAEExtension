package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.api.client.ECOCellModels;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.jspecify.annotations.NonNull;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ECODriveModel implements DynamicBlockStateModel {
    public static final Identifier DRIVE_EMPTY = NeoECOAE.id("block/eco_drive_empty");
    public static final Identifier DRIVE_FULL = NeoECOAE.id("block/eco_drive_full");

    private final BlockStateModelPart baseModel;
    private final Map<Item, BlockStateModelPart> cellModels;
    private final BlockStateModelPart defaultCellModel;

    public ECODriveModel(BlockStateModelPart baseModel, Map<Item, BlockStateModelPart> cellModels, BlockStateModelPart defaultCellModel) {
        this.baseModel = baseModel;
        this.cellModels = cellModels;
        this.defaultCellModel = defaultCellModel;
    }

    @Override
    public Material.@NonNull Baked particleMaterial() {
        return baseModel.particleMaterial();
    }

    @Override
    public @BakedQuad.MaterialFlags int materialFlags() {
        return baseModel.materialFlags();
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        parts.add(baseModel);

        level.getBlockEntity(pos, NEBlockEntities.ECO_DRIVE.get()).ifPresent(be -> {
            var cell = be.getCellStack();
            if (cell != null && !cell.isEmpty()) {
                parts.add(cellModels.getOrDefault(cell.getItem(), defaultCellModel));
            }
        });
    }

    public record Unbaked(Variant variant) implements CustomUnbakedBlockStateModel {
        public static final Identifier ID = NeoECOAE.id("eco_drive");
        public static MapCodec<Unbaked> MAP_CODEC = Variant.MAP_CODEC.xmap(Unbaked::new, Unbaked::variant);


        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return MAP_CODEC;
        }

        @Override
        public BlockStateModel bake(ModelBaker modelBakery) {
            final Map<Item, BlockStateModelPart> cellModels = new IdentityHashMap<>();
            ModelState modelState = variant.modelState().asModelState();

            var baseModel = SimpleModelWrapper.bake(modelBakery, variant.modelLocation(), modelState);

            for (var entry : ECOCellModels.getRegistry().entrySet()) {
                var model = entry.getValue();
                cellModels.put(entry.getKey(), SimpleModelWrapper.bake(modelBakery, model, modelState));
            }

            var defaultCellModel = SimpleModelWrapper.bake(modelBakery, ECOCellModels.DEFAULT_MODEL, modelState);

            return new ECODriveModel(baseModel, cellModels, defaultCellModel);
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            resolver.markDependency(variant.modelLocation());

            // bake all cell models
            resolver.markDependency(ECOCellModels.DEFAULT_MODEL);
            ECOCellModels.getRegistry().values().forEach(resolver::markDependency);
        }
    }
}
