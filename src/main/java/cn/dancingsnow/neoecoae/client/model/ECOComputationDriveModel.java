package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.client.ECOComputationModels;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import com.mojang.math.Transformation;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.ComposedModelState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ECOComputationDriveModel implements DynamicBlockStateModel {

    private final BlockStateModelPart baseModel;
    private final Map<Item, Entry> cellModels;
    private final Map<IECOTier, Entry> cableDownModels;
    private final Map<IECOTier, Entry> cableUpModels;

    public ECOComputationDriveModel(BlockStateModelPart baseModel, Map<Item, Entry> cellModels, Map<IECOTier, Entry> cableDownModels, Map<IECOTier, Entry> cableUpModels) {
        this.baseModel = baseModel;
        this.cellModels = cellModels;
        this.cableDownModels = cableDownModels;
        this.cableUpModels = cableUpModels;
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

        level.getBlockEntity(pos, NEBlockEntities.COMPUTATION_DRIVE.get()).ifPresent(be -> {
            ItemStack cellStack = be.getCellStack();
            boolean formed = be.isFormed();
            boolean shouldCellWork = false;
            boolean hasCell = cellStack != null && !cellStack.isEmpty();

            IECOTier cableTier = be.getTier();
            // cell model part
            if (hasCell && cellStack.getItem() instanceof ECOComputationCellItem item) {
                var model = cellModels.get(cellStack.getItem());
                // cell not empty
                shouldCellWork = formed && be.getTier() != null && item.getTier().compareTo(be.getTier()) <= 0;
                if (model != null) {
                    if (shouldCellWork) {
                        parts.add(model.formedModel());
                    } else {
                        parts.add(model.normalModel());
                    }
                }
                if (shouldCellWork) {
                    cableTier = item.getTier();
                }
            }

            // cable model part
            if (formed) {
                Entry model = be.isLowerDrive() ? cableDownModels.get(cableTier) : cableUpModels.get(cableTier);
                if (model != null) {
                    if (shouldCellWork) {
                        parts.add(model.formedModel());
                    } else {
                        parts.add(model.normalModel());
                    }
                }
            }
        });
    }

    public record Unbaked(Variant variant) implements CustomUnbakedBlockStateModel {
        public static final Identifier ID = NeoECOAE.id("eco_computation_drive");
        public static final MapCodec<Unbaked> MAP_CODEC = Variant.MAP_CODEC.xmap(Unbaked::new, Unbaked::variant);

        private static final Transformation upsideDown = new Transformation(new Matrix4f().rotateZ((float) Math.PI));

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return MAP_CODEC;
        }

        @Override
        public BlockStateModel bake(ModelBaker modelBakery) {
            final Map<Item, Entry> cellModels = new IdentityHashMap<>();
            final Map<IECOTier, Entry> cableDownModels = new IdentityHashMap<>();
            final Map<IECOTier, Entry> cableUpModels = new IdentityHashMap<>();

            ModelState modelState = variant.modelState().asModelState();
            var baseModel = SimpleModelWrapper.bake(modelBakery, variant.modelLocation(), modelState);

            for (var entry : ECOComputationModels.getCellRegistry().entrySet()) {
                ECOComputationModels.Entry model = entry.getValue();
                cellModels.put(entry.getKey(), new Entry(
                    SimpleModelWrapper.bake(modelBakery, model.normalModel(), modelState),
                    SimpleModelWrapper.bake(modelBakery, model.formedModel(), modelState)
                ));
            }

            for (var entry : ECOComputationModels.getCableRegistry().entrySet()) {
                ECOComputationModels.Entry model = entry.getValue();
                cableUpModels.put(entry.getKey(), new Entry(
                    SimpleModelWrapper.bake(modelBakery, model.normalModel(), modelState),
                    SimpleModelWrapper.bake(modelBakery, model.formedModel(), modelState)
                ));
                cableDownModels.put(entry.getKey(), new Entry(
                    SimpleModelWrapper.bake(modelBakery, model.normalModel(), new ComposedModelState(modelState, upsideDown)),
                    SimpleModelWrapper.bake(modelBakery, model.formedModel(), new ComposedModelState(modelState, upsideDown))
                ));
            }

            return new ECOComputationDriveModel(baseModel, cellModels, cableDownModels, cableUpModels);
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            resolver.markDependency(variant.modelLocation());

            // cable models
            ECOComputationModels.getCableRegistry().values().forEach(entry -> {
                resolver.markDependency(entry.normalModel());
                resolver.markDependency(entry.formedModel());
            });

            // cell models
            ECOComputationModels.getCellRegistry().values().forEach(entry -> {
                resolver.markDependency(entry.formedModel());
                resolver.markDependency(entry.normalModel());
            });
        }
    }

    public record Entry(BlockStateModelPart normalModel, BlockStateModelPart formedModel) {
    }
}
