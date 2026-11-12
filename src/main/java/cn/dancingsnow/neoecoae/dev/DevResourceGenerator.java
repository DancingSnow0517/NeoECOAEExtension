package cn.dancingsnow.neoecoae.dev;

import appeng.api.AECapabilities;
import appeng.api.networking.GridServices;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Stable registry identities; the development-tools config gates visibility and execution. */
public final class DevResourceGenerator {
    private static final String DATA = "neoecoae_dev_generator";
    public static final BlockEntry<GeneratorBlock> BLOCK = REGISTRATE
        .block("dev_resource_generator", GeneratorBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .lang("Development Resource Generator")
        .blockstate(() -> (context, provider) -> {
            var model = net.minecraft.client.data.models.model.ModelTemplates.CUBE_ALL.create(context.get(),
                new net.minecraft.client.data.models.model.TextureMapping().put(
                    net.minecraft.client.data.models.model.TextureSlot.ALL,
                    new net.minecraft.client.resources.model.sprite.Material(NeoECOAE.id("item/eco_infinite_cell_component"))),
                provider.modelOutput);
            provider.create(context.get(), model);
        })
        .item((block, properties) -> new GeneratorItem(block, properties.stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)))
        .model(() -> (context, provider) -> provider.itemModelOutput.accept(context.get(),
            net.minecraft.client.data.models.model.ItemModelUtils.plainModel(NeoECOAE.id("item/eco_infinite_cell_component"))))
        .tab(NECreativeTabs.ECO.getKey(), (context, modifier) -> {
            if (cn.dancingsnow.neoecoae.config.DevToolsConfig.enabled()) modifier.accept(context);
        })
        .build()
        .register();
    private static final BlockEntityEntry<GeneratorBlockEntity> ENTITY = REGISTRATE
        .blockEntity("dev_resource_generator", GeneratorBlockEntity::new)
        .validBlock(BLOCK)
        .onRegister(type -> {
            BLOCK.get().setBlockEntity(GeneratorBlockEntity.class, type, null, null);
            AEBaseBlockEntity.registerBlockEntityItem(type, BLOCK.asItem());
        })
        .register();

    private DevResourceGenerator() {}

    public static void register(IEventBus bus) {
        GridServices.register(GeneratorGridService.class, GeneratorGridService.class);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> StorageCells.addCellHandler(new ICellHandler() {
            @Override public boolean isCell(ItemStack stack) { return stack.is(BLOCK.asItem()); }
            @Override public StorageCell getCellInventory(ItemStack stack, ISaveProvider host) {
                return isCell(stack) ? new GeneratorGridService.GeneratorCell(stack) : null;
            }
        })));
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
            event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ENTITY.get(), (be, side) -> be));
        NeoForge.EVENT_BUS.addListener(DevResourceGenerator::commands);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> GeneratorPool.clear());
        DevKeyBenchmark.register();
        DevNetworkProfiler.register();
    }

    static CompoundTag readItem(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(DATA);
    }

    static void writeItem(ItemStack stack, CompoundTag state) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(DATA, state));
    }

    private static void commands(RegisterCommandsEvent event) {
        var root = literal("neodev").requires(source -> net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(source)
                && cn.dancingsnow.neoecoae.config.DevToolsConfig.enabled())
            .then(literal("help").executes(context -> {
                var source = context.getSource();
                for (String line : List.of("/neodev give", "/neodev set <types> <min> <max> <ticks> [x y z] [slot]",
                        "/neodev fe <P> <Pmin> <Pmax> <C> <Cmin> <Cmax> <seed> <probe> [x y z] [slot]",
                        "/neodev info | reroll | fe_stop | fe_start | fe_reset | fe_report [x y z] [slot]",
                        "/neodev keybench [item|fluid|both] [calls] [ticks per round] (hold generator; no network needed)",
                        "/neodev keybench_status | keybench_stop | keybench_report",
                        "/neodev start <seconds> [x y z] [slot] (look at the drive; real network profile)",
                        "/neodev status | stop")) {
                    source.sendSuccess(() -> Component.literal(line).withStyle(net.minecraft.ChatFormatting.AQUA), false);
                }
                source.sendSuccess(() -> Component.translatable("tooltip.neoecoae.dev.fe_consumes").withStyle(net.minecraft.ChatFormatting.GOLD), false);
                return 1;
            }))
            .then(literal("give").executes(context -> {
                context.getSource().getPlayerOrException().getInventory().placeItemBackInInventory(BLOCK.asStack());
                return 1;
            }))
            .then(literal("set")
                .then(argument("types", IntegerArgumentType.integer(1, 65536))
                    .then(argument("minimum", LongArgumentType.longArg(1))
                        .then(argument("maximum", LongArgumentType.longArg(1))
                            .then(argument("interval", IntegerArgumentType.integer(1))
                                .executes(context -> modify(context, "set", null))
                                .then(positioned("set")))))))
            .then(literal("reroll").executes(context -> modify(context, "reroll", null))
                .then(positioned("reroll")))
            .then(literal("info").executes(context -> modify(context, "info", null))
                .then(positioned("info")))
            .then(feCommand());
        for (String action : List.of("fe_stop", "fe_start", "fe_reset", "fe_report")) {
            root.then(literal(action).executes(context -> modify(context, action, null)).then(positioned(action)));
        }
        DevKeyBenchmark.commands(root);
        DevNetworkProfiler.commands(root);
        event.getDispatcher().register(root);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> feCommand() {
        var end = argument("probe", BoolArgumentType.bool()).executes(context -> modify(context, "fe", null)).then(positioned("fe"));
        return literal("fe")
            .then(argument("producers", IntegerArgumentType.integer(0, 65536))
                .then(argument("produceMin", LongArgumentType.longArg(1))
                    .then(argument("produceMax", LongArgumentType.longArg(1))
                        .then(argument("consumers", IntegerArgumentType.integer(0, 65536))
                            .then(argument("consumeMin", LongArgumentType.longArg(1))
                                .then(argument("consumeMax", LongArgumentType.longArg(1))
                                    .then(argument("seed", LongArgumentType.longArg()).then(end))))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,
            net.minecraft.commands.arguments.coordinates.Coordinates> positioned(String action) {
        return argument("pos", BlockPosArgument.blockPos())
            .executes(context -> modify(context, action, BlockPosArgument.getLoadedBlockPos(context, "pos")))
            .then(argument("slot", IntegerArgumentType.integer(0, 63))
                .executes(context -> modify(context, action, BlockPosArgument.getLoadedBlockPos(context, "pos"))));
    }

    private static int modify(CommandContext<CommandSourceStack> context, String action, BlockPos pos) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = context.getSource();
        ItemStack stack = pos == null ? source.getPlayerOrException().getMainHandItem() : ItemStack.EMPTY;
        var entity = pos == null ? null : source.getLevel().getBlockEntity(pos);
        try {
            GeneratorState state;
            Runnable persist;
            if (entity instanceof GeneratorBlockEntity generator) {
                state = generator.state;
                persist = generator::setChanged;
            } else if (entity instanceof appeng.api.implementations.blockentities.IChestOrDrive drive
                    && entity instanceof AEBaseBlockEntity host) {
                int slot;
                try { slot = IntegerArgumentType.getInteger(context, "slot"); }
                catch (IllegalArgumentException absent) { slot = 0; }
                if (slot >= drive.getCellCount() || !(drive.getOriginalCellInventory(slot) instanceof GeneratorGridService.GeneratorCell cell))
                    throw new IllegalArgumentException("No mounted dev generator in slot " + slot + " (slots start at 0)");
                if (cell.state == null) cell.state = GeneratorState.read(readItem(cell.stack), source.registryAccess());
                state = cell.state;
                persist = () -> {
                    writeItem(cell.stack, state.write(source.registryAccess()));
                    host.setChanged();
                };
            } else if (pos == null && stack.is(BLOCK.asItem())) {
                state = GeneratorState.read(readItem(stack), source.registryAccess());
                persist = () -> writeItem(stack, state.write(source.registryAccess()));
            } else {
                throw new IllegalArgumentException("Hold a dev generator, or append its block/ME Drive coordinates and optional cell slot");
            }
            if (action.equals("set")) state.configure(new GeneratorSettings(
                IntegerArgumentType.getInteger(context, "types"), LongArgumentType.getLong(context, "minimum"),
                LongArgumentType.getLong(context, "maximum"), IntegerArgumentType.getInteger(context, "interval")));
            if (action.equals("reroll")) state.reroll();
            if (action.equals("fe")) state.configureFe(new FeWorkloadSettings(
                IntegerArgumentType.getInteger(context, "producers"), LongArgumentType.getLong(context, "produceMin"),
                LongArgumentType.getLong(context, "produceMax"), IntegerArgumentType.getInteger(context, "consumers"),
                LongArgumentType.getLong(context, "consumeMin"), LongArgumentType.getLong(context, "consumeMax"),
                LongArgumentType.getLong(context, "seed"), BoolArgumentType.getBool(context, "probe")));
            if (action.startsWith("fe_") && !action.equals("fe_report")) state.controlFe(action);
            if (action.equals("fe_report")) {
                if (state.fe == null) throw new IllegalArgumentException("Configure FE first with /neodev fe");
                var report = state.fe.report();
                report.put("running", state.feRunning);
                report.put("failure", state.feFailure);
                report.put("target", pos == null ? "held item; no live network traffic" : pos.toShortString());
                var directory = source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("neoecoae-dev-reports");
                java.nio.file.Files.createDirectories(directory);
                var file = directory.resolve("fe-" + java.util.UUID.randomUUID() + ".json");
                java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report), java.nio.file.StandardOpenOption.CREATE_NEW);
                source.sendSuccess(() -> Component.literal("FE report: " + file.toAbsolutePath()), false);
            }
            if (!action.equals("info") && !action.equals("fe_report")) persist.run();
            source.sendSuccess(() -> Component.literal(state.describe()), false);
            if (action.equals("info") && state.fe == null) {
                for (var key : state.selected.subList(0, Math.min(32, state.selected.size()))) source.sendSuccess(() -> key.getDisplayName().copy(), false);
            }
            return 1;
        } catch (IllegalArgumentException | java.io.IOException error) {
            source.sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }

    public static final class GeneratorBlock extends AEBaseEntityBlock<GeneratorBlockEntity> {
        public GeneratorBlock(Properties properties) { super(properties); }

        @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator) {
                generator.state = GeneratorState.read(readItem(stack), level.registryAccess());
                generator.setChanged();
            }
        }

        @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
            ItemStack stack = BLOCK.asStack();
            if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof GeneratorBlockEntity generator) {
                writeItem(stack, generator.state.write(params.getLevel().registryAccess()));
            }
            return List.of(stack);
        }
    }

    public static final class GeneratorItem extends BlockItem {
        public GeneratorItem(GeneratorBlock block, Properties properties) { super(block, properties); }
        @Override public Component getName(ItemStack stack) {
            return Component.translatable("item.neoecoae.dev_resource_generator");
        }
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
            super.appendHoverText(stack, context, display, lines, flag);
            var tooltip = new java.util.ArrayList<Component>();
            GeneratorTooltip.append(readItem(stack), cn.dancingsnow.neoecoae.config.DevToolsConfig.enabled(), tooltip);
            tooltip.forEach(lines);
        }
        @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            return cn.dancingsnow.neoecoae.config.DevToolsConfig.enabled() ? super.useOn(context) : net.minecraft.world.InteractionResult.FAIL;
        }
    }

    public static final class GeneratorBlockEntity extends AENetworkedBlockEntity {
        GeneratorState state = new GeneratorState();
        public GeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
            super(type, pos, blockState);
            getMainNode().setIdlePowerUsage(0);
        }
        @Override public void loadTag(net.minecraft.world.level.storage.ValueInput input) {
            super.loadTag(input);
            var tag = cn.dancingsnow.neoecoae.util.LegacyNbt.read(input);
            var registries = input.lookup();
            state = GeneratorState.read(tag.getCompoundOrEmpty(DATA), registries);
        }
        @Override public void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
            super.saveAdditional(output);
            output.store(DATA, net.minecraft.nbt.CompoundTag.CODEC, state.write(cn.dancingsnow.neoecoae.util.LegacyNbt.registries(this)));
        }
    }
}
