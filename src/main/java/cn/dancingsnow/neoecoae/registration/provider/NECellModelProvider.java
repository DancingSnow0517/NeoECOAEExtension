package cn.dancingsnow.neoecoae.registration.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.providers.RegistrateProvider;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.ModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class NECellModelProvider extends ModelProvider<BlockModelBuilder> implements RegistrateProvider {

    private final AbstractRegistrate<?> parent;

    public NECellModelProvider(AbstractRegistrate<?> parent, PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, parent.getModid(), "block/cell", BlockModelBuilder::new, existingFileHelper);
        this.parent = parent;
    }

    @Override
    protected void registerModels() {
        parent.genData(NEProviderTypes.CELL_MODEL, this);
    }

    public void cellModel(String name) {
        cellModel(name, modLoc("block/cell/led/" + name));
    }

    public void cellModel(String name, ResourceLocation led) {
        withExistingParent(name, NeoECOAE.id("block/cell/storage_cell"))
            .texture("2", led);
    }

    @Override
    public String getName() {
        return "Cell Models";
    }

    @Override
    public LogicalSide getSide() {
        return LogicalSide.CLIENT;
    }
}
