package cn.dancingsnow.neoecoae.util;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.blocks.ECOMachineCasing;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationCoolingController;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationDrive;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationParallelCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationThreadingCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationTransmitter;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingPatternBus;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingWorker;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.client.item.ECOStorageCellStateTintSource;
import cn.dancingsnow.neoecoae.client.model.ECOComputationDriveModel;
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
import net.minecraft.resources.Identifier;
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
    public static final ModelTemplate SMART_COOLING_MODULE = createTemplate("smart_cooling_module_base",
        TextureSlot.SIDE, TextureSlot.FRONT, TextureSlot.BOTTOM, TextureSlot.TOP);

    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator> cellModel(String type, String size) {
        // main ships hand-written models (textures under item/eco_cells/) in models/item/cell/.
        // Imported item model IDs retain their existing energy family name; their textures use appflux/fe.
        String assetType = type.equals("fe") ? "energy" : type;
        return importedTintedCellModel("eco_%s_cell_%s".formatted(assetType, size));
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

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> storageEnergyCell(String level) {
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
            var emptyModel = customBlockStateModel(new ECODriveModel.Unbaked(new Variant(NeoECOAE.id("block/eco_drive_empty"))));
            var fullModel = customBlockStateModel(new ECODriveModel.Unbaked(new Variant(NeoECOAE.id("block/eco_drive_full"))));

            var propertyDispatch = PropertyDispatch.initial(ECODriveBlock.HAS_CELL)
                .select(false, emptyModel)
                .select(true, fullModel);

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).withUnbaked(createDriverFacingDispatch()));
            prov.registerSimpleItemModel(ctx.get(), NeoECOAE.id("block/eco_drive_empty"));
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationDrive() {
        return (ctx, prov) -> {
            var emptyModel = customBlockStateModel(new ECOComputationDriveModel.Unbaked(new Variant(NeoECOAE.id("block/computation_drive_empty"))));
            var fullModel = customBlockStateModel(new ECOComputationDriveModel.Unbaked(new Variant(NeoECOAE.id("block/computation_drive_full"))));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationDrive.HAS_CELL)
                .select(false, emptyModel)
                .select(true, fullModel);

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).withUnbaked(createDriverFacingDispatch()));
            prov.registerSimpleItemModel(ctx.get(), NeoECOAE.id("block/computation_drive_empty"));
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> integratedWorkingStation() {
        return (ctx, prov) -> {
            var model = ctx.getId().withPrefix("block/");
            var workingModel = ctx.getId().withPrefix("block/").withSuffix("_on");

            // Unformed: plain/working model. Formed (large multiblock controller): module-specific model.
            var propertyDispatch = PropertyDispatch.initial(
                ECOIntegratedWorkingStation.WORKING,
                ECOIntegratedWorkingStation.FORMED,
                ECOIntegratedWorkingStation.MODULE,
                ECOIntegratedWorkingStation.SMART_COOLING
            ).generate((working, formed, module, smartCooling) -> {
                if (formed) {
                    return BlockModelGenerators.plainVariant(prov.modLoc(module.modelPath(working, smartCooling)));
                }
                return BlockModelGenerators.plainVariant(working ? workingModel : model);
            });

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), model);
        };
    }

    /** Single existing block model ({@code block/<name>}) for every state, plus an item model using it. */
    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> simpleExistingWithItem() {
        return (ctx, prov) -> {
            var model = prov.modLoc("block/" + ctx.getName());
            prov.create(ctx.get(), model);
            // The item definition comes from the block's default .item() builder.
        };
    }

    /** Blockstate is hand-written in resources. */
    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> handWritten() {
        return (ctx, prov) -> {};
    }

    /** Item definition pointing to a hand-written {@code item/<name>} model. */
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator> existingItemModel() {
        return (ctx, prov) -> prov.itemModelOutput.accept(ctx.get(), ItemModelUtils.plainModel(NeoECOAE.id("item/" + ctx.getName())));
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> smartCoolingModule() {
        return (ctx, prov) -> {
            var model = SMART_COOLING_MODULE.create(
                ctx.get(),
                new TextureMapping()
                    .put(TextureSlot.SIDE, new Material(prov.modLoc("block/working_station/smart_cooling_module_side")))
                    .put(TextureSlot.FRONT, new Material(prov.modLoc("block/working_station/smart_cooling_module_front")))
                    .put(TextureSlot.BOTTOM, new Material(prov.modLoc("block/working_station/smart_cooling_module_side")))
                    .put(TextureSlot.TOP, new Material(prov.modLoc("block/working_station/smart_cooling_module_top"))),
                prov.modelOutput
            );
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get(), BlockModelGenerators.plainVariant(model)));
            // Item definition comes from the block's .simpleItem().
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> fxMonitorCore() {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/crafting_monitor");
            var formedModel = prov.modLoc("block/crafting_monitor_formed");
            var workingModel = prov.modLoc("block/crafting_monitor_working");

            var propertyDispatch = PropertyDispatch.initial(ECOCraftingWorker.FORMED, ECOCraftingWorker.WORKING).generate((formed, working) -> {
                if (working) {
                    return BlockModelGenerators.plainVariant(workingModel);
                }
                return BlockModelGenerators.plainVariant(formed ? formedModel : unformedModel);
            });

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationTransmitter() {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/computation_transmitter");
            var formedModel = prov.modLoc("block/computation_transmitter_formed");

            var propertyDispatch = PropertyDispatch.initial(ECOComputationTransmitter.FORMED)
                .select(false, BlockModelGenerators.plainVariant(unformedModel))
                .select(true, BlockModelGenerators.plainVariant(formedModel));

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingWorker() {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/crafting_worker");
            var formedModel = prov.modLoc("block/crafting_worker_formed");
            var workingModel = prov.modLoc("block/crafting_worker_working");

            var propertyDispatch = PropertyDispatch.initial(ECOCraftingWorker.FORMED, ECOCraftingWorker.WORKING).generate((formed, working) -> {
                if (formed) {
                    if (working) {
                        return BlockModelGenerators.plainVariant(workingModel);
                    } else {
                        return BlockModelGenerators.plainVariant(formedModel);
                    }
                } else {
                    return BlockModelGenerators.plainVariant(formedModel);
                }
            });

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingPatternBus() {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/crafting_pattern_bus");
            var formedModel = prov.modLoc("block/crafting_pattern_bus_formed");

            var propertyDispatch = PropertyDispatch.initial(ECOCraftingPatternBus.FORMED)
                .select(true, BlockModelGenerators.plainVariant(formedModel))
                .select(false, BlockModelGenerators.plainVariant(unformedModel));

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingVent() {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/crafting_vent");
            var formedModel = prov.modLoc("block/crafting_vent_formed");

            var propertyDispatch = PropertyDispatch.initial(ECOMachineCasing.FORMED)
                .select(true, BlockModelGenerators.plainVariant(formedModel))
                .select(false, BlockModelGenerators.plainVariant(unformedModel));

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingCasing() {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/crafting_casing");
            var formedModel = prov.modLoc("block/crafting_casing_formed");

            var propertyDispatch = PropertyDispatch.initial(ECOMachineCasing.FORMED)
                .select(true, BlockModelGenerators.plainVariant(formedModel))
                .select(false, BlockModelGenerators.plainVariant(unformedModel));

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> storageSystem(String level) {
        return (ctx, prov) -> {
            var offModel = prov.modLoc("block/storage_controller/controller_%s_off".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(
                ECOStorageSystemBlock.FORMED,
                ECOStorageSystemBlock.MIRRORED,
                ECOStorageSystemBlock.STORAGE_MODE
            ).generate((formed, mirrored, mode) -> {
                if (!formed) {
                    return BlockModelGenerators.plainVariant(offModel);
                }
                String suffix = switch (mode) {
                    case INPUT -> "_input";
                    case OUTPUT -> "_output";
                    default -> "";
                };
                return BlockModelGenerators.plainVariant(prov.modLoc(
                    "block/storage_controller/controller_%s_formed%s%s".formatted(level, suffix, mirrored ? "_mirrored" : "")));
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), offModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingParallelCore(String level) {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/crafting_core/parallel_core_%s".formatted(level));
            var formedModel = prov.modLoc("block/crafting_core/parallel_core_%s_formed".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationParallelCore.FORMED).generate((formed) -> {
                if (formed) {
                    return BlockModelGenerators.plainVariant(formedModel);
                } else {
                    return BlockModelGenerators.plainVariant(unformedModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingSystem(String level) {
        return (ctx, prov) -> {
            var offModel = prov.modLoc("block/crafting_controller/controller_%s_off".formatted(level));
            var formedModel = prov.modLoc("block/crafting_controller/controller_%s_formed".formatted(level));
            var formedMirroredModel = prov.modLoc("block/crafting_controller/controller_%s_formed_mirrored".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOCraftingSystem.FORMED, ECOCraftingSystem.MIRRORED).generate((formed, mirrored) -> {
                if (formed) {
                    if (mirrored) {
                        return BlockModelGenerators.plainVariant(formedMirroredModel);
                    } else {
                        return BlockModelGenerators.plainVariant(formedModel);
                    }
                } else {
                    return BlockModelGenerators.plainVariant(offModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), offModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationSystem(String level) {
        return (ctx, prov) -> {
            var offModel = prov.modLoc("block/computation_controller/controller_%s_off".formatted(level));
            var formedModel = prov.modLoc("block/computation_controller/controller_%s_formed".formatted(level));
            var formedMirroredModel = prov.modLoc("block/computation_controller/controller_%s_formed_mirrored".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationSystem.FORMED, ECOComputationSystem.MIRRORED).generate((formed, mirrored) -> {
                if (formed) {
                    if (mirrored) {
                        return BlockModelGenerators.plainVariant(formedMirroredModel);
                    } else {
                        return BlockModelGenerators.plainVariant(formedModel);
                    }
                } else {
                    return BlockModelGenerators.plainVariant(offModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), offModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationParallelCore(String level) {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/computation_core/parallel_core_%s".formatted(level));
            var formedModel = prov.modLoc("block/computation_core/parallel_core_%s_formed".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationParallelCore.FORMED).generate((formed) -> {
                if (formed) {
                    return BlockModelGenerators.plainVariant(formedModel);
                } else {
                    return BlockModelGenerators.plainVariant(unformedModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationThreadingCore(String level) {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/computation_core/threading_core_%s".formatted(level));
            var formedModel = prov.modLoc("block/computation_core/threading_core_%s_formed".formatted(level));
            var workingModel = prov.modLoc("block/computation_core/threading_core_%s_working".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationThreadingCore.FORMED, ECOComputationThreadingCore.WORKING).generate((formed, working) -> {
                if (formed) {
                    if (working) {
                        return BlockModelGenerators.plainVariant(workingModel);
                    } else {
                        return BlockModelGenerators.plainVariant(formedModel);
                    }
                } else {
                    return BlockModelGenerators.plainVariant(unformedModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationCoolingController(String level) {
        return (ctx, prov) -> {
            var offModel = prov.modLoc("block/computation_cooling_controller/controller_%s_off".formatted(level));
            var formedModel = prov.modLoc("block/computation_cooling_controller/controller_%s_formed".formatted(level));
            var formedMirroredModel = prov.modLoc("block/computation_cooling_controller/controller_%s_formed_mirrored".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationCoolingController.FORMED, ECOComputationCoolingController.MIRRORED).generate((formed, mirrored) -> {
                if (formed) {
                    if (mirrored) {
                        return BlockModelGenerators.plainVariant(formedMirroredModel);
                    } else {
                        return BlockModelGenerators.plainVariant(formedModel);
                    }
                } else {
                    return BlockModelGenerators.plainVariant(offModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), offModel);
        };
    }

    /** Imported cell model {@code item/cell/<model>} with the AE2 status tint on layer 2. */
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator> importedTintedCellModel(String model) {
        return (ctx, prov) -> prov.itemModelOutput.accept(
            ctx.get(),
            ItemModelUtils.tintedModel(
                prov.modLoc("item/cell/" + model),
                new Constant(-1),
                new Constant(-1),
                ECOStorageCellStateTintSource.INSTANCE
            )
        );
    }

    /** Untiered cell icon: housing + status light (status tint on layer 1). */
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator> infiniteCellModel(String housing) {
        return (ctx, prov) -> {
            var model = ModelTemplates.TWO_LAYERED_ITEM.create(
                ctx.get(),
                TextureMapping.layered(
                    new Material(prov.modLoc("item/eco_cells/" + housing)),
                    new Material(prov.modLoc("item/eco_cells/cell_status_light"))
                ),
                prov.modelOutput
            );
            prov.itemModelOutput.accept(ctx.get(), ItemModelUtils.tintedModel(
                model, new Constant(-1), ECOStorageCellStateTintSource.INSTANCE));
        };
    }

    /** Flat generated item model with an explicit texture. */
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator> generatedItem(Identifier texture) {
        return (ctx, prov) -> prov.generateFlatItem(ctx.get(), new Material(texture));
    }

    private static ModelTemplate createTemplate(String id, TextureSlot... slots) {
        return new ModelTemplate(Optional.of(NeoECOAE.id(id).withPrefix("block/")), Optional.empty(), slots);
    }

    private static MultiVariant customBlockStateModel(CustomUnbakedBlockStateModel model) {
        return MultiVariant.of(new CustomBlockStateModelBuilder.Simple(model));
    }

    private static PropertyDispatch<UnbakedMutator> createDriverFacingDispatch() {
        return PropertyDispatch.modifyUnbaked(BlockStateProperties.HORIZONTAL_FACING).generate(facing -> {
            VariantMutator mutator = switch (facing) {
                case EAST -> BlockModelGenerators.Y_ROT_90;
                case SOUTH -> BlockModelGenerators.Y_ROT_180;
                case WEST -> BlockModelGenerators.Y_ROT_270;
                default -> BlockModelGenerators.NOP;
            };
            return UnbakedMutator.builder()
                .add(ECODriveModel.Unbaked.class, unbaked -> new ECODriveModel.Unbaked(unbaked.variant().with(mutator)))
                .add(ECOComputationDriveModel.Unbaked.class, unbaked -> new ECOComputationDriveModel.Unbaked(unbaked.variant().with(mutator)))
                .build();
        });
    }
}
