package cn.dancingsnow.neoecoae.registration.provider;

import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.providers.RegistrateProvider;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.data.PackOutput;
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

    @Override
    public String getName() {
        return "Cell Models";
    }

    @Override
    public LogicalSide getSide() {
        return LogicalSide.CLIENT;
    }
}
