package cn.dancingsnow.neoecoae.dev;


import appeng.api.networking.GridServices;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.grid.AENetworkBlockEntity;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Loaded only by the explicit runClient property in an unpackaged development environment. */
public final class DevResourceGenerator {
    private static final String DATA = "neoecoae_dev_generator";
    public static final BlockEntry<GeneratorBlock> BLOCK = REGISTRATE
        .block("dev_resource_generator", GeneratorBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .lang("Dev Random Resource Generator / Cell")
        .item((block, properties) -> new GeneratorItem(block, properties.stacksTo(1)))
        .tab(NECreativeTabs.ECO.getKey())
        .build()
        .register();
    private static final BlockEntityEntry<GeneratorBlockEntity> ENTITY = REGISTRATE
        .blockEntity("dev_resource_generator", GeneratorBlockEntity::new)
        .validBlock(BLOCK)
        .onRegister(type -> {
            BLOCK.get().setBlockEntity(GeneratorBlockEntity.class, type, null, null);
            AEBaseBlockEntity.registerBlockEntityItem(type, BLOCK.asItem());
            cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities.register(
                appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST, type, (be, side) -> be);
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
        MinecraftForge.EVENT_BUS.addListener(DevResourceGenerator::commands);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> GeneratorPool.clear());
    }

    static CompoundTag readItem(ItemStack stack) {
        return stack.getOrCreateTag().getCompound(DATA);
    }

    static void writeItem(ItemStack stack, CompoundTag state) {
        stack.getOrCreateTag().put(DATA, state);
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("neodev").requires(source -> source.hasPermission(2))
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
                                .then(argument("pos", BlockPosArgument.blockPos())
                                    .executes(context -> modify(context, "set", BlockPosArgument.getLoadedBlockPos(context, "pos")))))))))
            .then(literal("reroll").executes(context -> modify(context, "reroll", null))
                .then(argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> modify(context, "reroll", BlockPosArgument.getLoadedBlockPos(context, "pos")))))
            .then(literal("info").executes(context -> modify(context, "info", null))
                .then(argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> modify(context, "info", BlockPosArgument.getLoadedBlockPos(context, "pos"))))));
    }

    private static int modify(CommandContext<CommandSourceStack> context, String action, BlockPos pos) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = context.getSource();
        ItemStack stack = pos == null ? source.getPlayerOrException().getMainHandItem() : ItemStack.EMPTY;
        var entity = pos == null ? null : source.getLevel().getBlockEntity(pos);
        if (pos == null ? !stack.is(BLOCK.asItem()) : !(entity instanceof GeneratorBlockEntity)) {
            source.sendFailure(Component.literal("Hold a dev generator, or append its placed block coordinates."));
            return 0;
        }
        try {
            GeneratorState state = entity instanceof GeneratorBlockEntity generator ? generator.state
                : GeneratorState.read(readItem(stack), source.registryAccess());
            if (action.equals("set")) state.configure(new GeneratorSettings(
                IntegerArgumentType.getInteger(context, "types"), LongArgumentType.getLong(context, "minimum"),
                LongArgumentType.getLong(context, "maximum"), IntegerArgumentType.getInteger(context, "interval")));
            if (action.equals("reroll")) state.reroll();
            if (!action.equals("info")) {
                if (entity instanceof GeneratorBlockEntity generator) generator.setChanged();
                else writeItem(stack, state.write(source.registryAccess()));
            }
            source.sendSuccess(() -> Component.literal(state.describe()), false);
            if (action.equals("info")) {
                for (var key : state.selected.subList(0, Math.min(32, state.selected.size()))) source.sendSuccess(() -> key.getDisplayName().copy(), false);
            }
            return 1;
        } catch (IllegalArgumentException error) {
            source.sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }

    public static final class GeneratorBlock extends AEBaseEntityBlock<GeneratorBlockEntity> {
        public GeneratorBlock(Properties properties) { super(properties); }

        @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator) {
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
            return Component.translatableWithFallback("item.neoecoae.dev_resource_generator", "Dev Random Resource Generator / Cell");
        }
        @Override public void appendHoverText(ItemStack stack, Level context, List<Component> lines, TooltipFlag flag) {
            super.appendHoverText(stack, context, lines, flag);
            lines.add(Component.literal("DEV: Place and cable, or install in an ME Drive/Chest."));
            lines.add(Component.literal("/neodev set <types> <min> <max> <ticks> [x y z]"));
            lines.add(Component.literal("/neodev reroll [x y z] | /neodev info [x y z]"));
        }
    }

    public static final class GeneratorBlockEntity extends AENetworkBlockEntity {
        GeneratorState state = new GeneratorState();
        public GeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
            super(type, pos, blockState);
            getMainNode().setIdlePowerUsage(0);
        }
        @Override public void loadTag(CompoundTag tag) {
            super.loadTag(tag);
            state = GeneratorState.read(tag.getCompound(DATA), null);
        }
        @Override public void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.put(DATA, state.write(null));
        }
    }
}
