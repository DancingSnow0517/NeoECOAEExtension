package cn.dancingsnow.neoecoae.util;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.client.item.ECOStorageCellStateTintSource;
import cn.dancingsnow.neoecoae.client.model.ECODriveModel;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateBlockModelGenerator;
import com.tterrag.registrate.providers.generators.RegistrateItemModelGenerator;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.color.item.Constant;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.client.model.generators.blockstate.CustomBlockStateModelBuilder;
import net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator;

import java.util.Optional;

import static net.minecraft.client.data.models.BlockModelGenerators.ROTATIONS_COLUMN_WITH_FACING;
import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ECOModelUtil {
    public static final ModelTemplate CASING = createTemplate("casing_base", TextureSlot.TEXTURE);

    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator> cellModel(
        String type,
        String size
    ) {
        return (ctx, prov) -> {
            var model = ModelTemplates.THREE_LAYERED_ITEM.create(
                ctx.get(),
                TextureMapping.layered(
                    prov.modItemTexture("eco_%s_cell_housing".formatted(type)),
                    prov.modItemTexture("eco_cell_light_" + size),
                    prov.modItemTexture("eco_storage_cell_led")
                ),
                prov.modelOutput
            );
            prov.itemModelOutput.accept(
                ctx.get(),
                ItemModelUtils.tintedModel(
                    model,
                    new Constant(-1),
                    new Constant(-1),
                    ECOStorageCellStateTintSource.INSTANCE
                )
            );
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> casing() {
        return (ctx, prov) -> {
            prov.generateWithTemplate(
                ctx.get(),
                CASING,
                TextureMapping.singleSlot(TextureSlot.TEXTURE, new Material(ctx.getId().withPrefix("block/")))
            );
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> simpleExisting() {
        return (ctx, provider) -> provider.create(ctx.get(), provider.modLoc("block/" + ctx.getName()));
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> horizontalExisting() {
        return (ctx, prov) -> {
            var model = ctx.getId().withPrefix("block/");
            prov.generateHorizontalBlock(ctx.get(), BlockModelGenerators.plainVariant(model));
            prov.registerSimpleItemModel(ctx.get(), model);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> storageEnergyCell(
        String level
    ) {
        return (ctx, prov) -> {
            var propertyDispatch = PropertyDispatch.initial(ECOEnergyCellBlock.LEVEL).generate(l -> BlockModelGenerators.plainVariant(
                prov.modLoc("block/storage_energy_cell/cell_%s_%d".formatted(level, l))));
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(
                BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(
                ctx.get(),
                prov.modLoc("block/storage_energy_cell/cell_%s_0".formatted(level))
            );
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> quartzCluster() {
        return (ctx, prov) -> {
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(
                ctx.get(),
                plainVariant(
                    ModelTemplates.CROSS.extend().build().create(
                        ctx.get(),
                        TextureMapping.cross(ctx.get()),
                        prov.modelOutput
                    )
                )
            ).with(ROTATIONS_COLUMN_WITH_FACING));
            var model = ModelTemplates.FLAT_ITEM.create(
                ctx.get().asItem(),
                TextureMapping.layer0(ctx.get()),
                prov.modelOutput
            );
            prov.itemModelOutput.accept(ctx.get().asItem(), ItemModelUtils.plainModel(model));
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> drive() {
        return (ctx, prov) -> {
            var emptyModel = customBlockStateModel(new ECODriveModel.Unbaked(new Variant(ECODriveModel.DRIVE_EMPTY)));
            var fullModel = customBlockStateModel(new ECODriveModel.Unbaked(new Variant(ECODriveModel.DRIVE_FULL)));

            var propertyDispatch = PropertyDispatch.initial(ECODriveBlock.HAS_CELL)
                .select(false, emptyModel)
                .select(true, fullModel);

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).withUnbaked(
                createDriverFacingDispatch()));
            prov.registerSimpleItemModel(ctx.get(), ECODriveModel.DRIVE_EMPTY);
        };
    }

    private static ModelTemplate createTemplate(String id, TextureSlot... slots) {
        return new ModelTemplate(Optional.of(NeoECOAE.id(id).withPrefix("block/")), Optional.empty(), slots);
    }

    private static MultiVariant customBlockStateModel(CustomUnbakedBlockStateModel model) {
        return MultiVariant.of(new CustomBlockStateModelBuilder.Simple(model));
    }

    private static PropertyDispatch<UnbakedMutator> createDriverFacingDispatch() {
        return PropertyDispatch.modifyUnbaked(BlockStateProperties.HORIZONTAL_FACING).generate(facing ->
            UnbakedMutator.builder()
                .add(
                    ECODriveModel.Unbaked.class, unbaked -> {
                        VariantMutator mutator = switch (facing) {
                            case EAST -> BlockModelGenerators.Y_ROT_90;
                            case SOUTH -> BlockModelGenerators.Y_ROT_180;
                            case WEST -> BlockModelGenerators.Y_ROT_270;
                            default -> BlockModelGenerators.NOP;
                        };
                        return new ECODriveModel.Unbaked(unbaked.variant().with(mutator));
                    }
                )
                .build()
        );
    }
}
