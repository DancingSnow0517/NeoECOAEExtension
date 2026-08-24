package cn.dancingsnow.neoecoae.data.tag;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import com.tterrag.registrate.providers.RegistrateItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

public class NETagGenerator {
    private static final ResourceLocation GTCEU_ALUMINIUM_DUST =
            ResourceLocation.fromNamespaceAndPath("gtceu", "aluminium_dust");
    private static final ResourceLocation GTCEU_ALUMINUM_DUST =
            ResourceLocation.fromNamespaceAndPath("gtceu", "aluminum_dust");
    private static final ResourceLocation GTL_INFINITE_CELL_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("gtlcore", "infinite_cell_component");
    private static final ResourceLocation GTO_INFINITE_CELL_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("gtocore", "infinite_cell_component");
    private static final ResourceLocation GTCEU_SILICON_DUST =
            ResourceLocation.fromNamespaceAndPath("gtceu", "silicon_dust");
    private static final ResourceLocation GTL_CERTUS_QUARTZ_CRYSTAL_BLOCK =
            ResourceLocation.fromNamespaceAndPath("gtlcore", "certus_quartz_crystal_block");
    private static final ResourceLocation GTL_CERTUS_QUARTZ_BLOCK =
            ResourceLocation.fromNamespaceAndPath("gtlcore", "certus_quartz_block");

    public static void itemTag(RegistrateItemTagsProvider provider) {
        provider.addTag(NETags.Items.CRYSTAL_INGOT_BASE).add(Items.DIAMOND).add(Items.EMERALD);

        provider.addTag(NETags.Items.SUPERCONDUCTIVE_INGOT_BASE)
                .add(Items.DIAMOND)
                .add(Items.EMERALD);

        provider.addTag(NETags.Items.ALUMINIUM_DUST)
                .addOptional(GTCEU_ALUMINIUM_DUST)
                .addOptional(GTCEU_ALUMINUM_DUST);
        provider.addTag(NETags.Items.ALUMINUM_DUST)
                .addOptional(GTCEU_ALUMINUM_DUST)
                .addOptional(GTCEU_ALUMINIUM_DUST);

        provider.addTag(NETags.Items.SILICON_DUST).add(AEItems.SILICON.asItem()).addOptional(GTCEU_SILICON_DUST);
        provider.addTag(NETags.Items.SILICON_PLATE).add(AEItems.SILICON_PRINT.asItem());
        provider.addTag(NETags.Items.CERTUS_QUARTZ_BLOCK)
                .add(AEBlocks.QUARTZ_BLOCK.asItem())
                .addOptional(GTL_CERTUS_QUARTZ_CRYSTAL_BLOCK)
                .addOptional(GTL_CERTUS_QUARTZ_BLOCK);

        provider.addTag(NETags.Items.INFINITE_CELL_COMPONENTS)
                .add(NEItems.ECO_INFINITE_CELL_COMPONENT.get())
                .addOptional(GTL_INFINITE_CELL_COMPONENT)
                .addOptional(GTO_INFINITE_CELL_COMPONENT);
    }
}
