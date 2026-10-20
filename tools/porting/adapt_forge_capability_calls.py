"""Replace NeoForge capability lookups with Forge LazyOptional lookups."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

def main():
    for source in (ROOT / 'src').rglob('*.java'):
        before = source.read_text(encoding='utf-8')
        after = before.replace('import net.minecraftforge.capabilities.Capabilities;',
                               'import net.minecraftforge.common.capabilities.ForgeCapabilities;')
        after = re.sub(r'level\.getCapability\(\s*Capabilities\.FluidHandler\.BLOCK,\s*',
                       'cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities.find(level, ', after)
        # The helper accepts position before capability, avoiding a world lookup that could load a chunk.
        after = after.replace('find(level, neighborPos, face.getOpposite())',
                              'find(level, neighborPos, ForgeCapabilities.FLUID_HANDLER, face.getOpposite())')
        after = after.replace('find(level, pos.relative(face), face.getOpposite())',
                              'find(level, pos.relative(face), ForgeCapabilities.FLUID_HANDLER, face.getOpposite())')
        after = after.replace('stack.getCapability(Capabilities.ItemHandler.ITEM)',
                              'stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null)')
        if source.name.endswith('HatchBlockEntity.java'):
            after = after.replace('saveAdditional(CompoundTag data, HolderLookup.Provider registries)',
                                  'saveAdditional(CompoundTag data)')
            after = after.replace('loadTag(CompoundTag data, HolderLookup.Provider registries)',
                                  'loadTag(CompoundTag data)')
            after = after.replace('super.saveAdditional(data, registries)', 'super.saveAdditional(data)')
            after = after.replace('super.loadTag(data, registries)', 'super.loadTag(data)')
            after = after.replace('tank.writeToNBT(registries, data)', 'tank.writeToNBT(data)')
            after = after.replace('tank.readFromNBT(registries, data)', 'tank.readFromNBT(data)')
        if before != after:
            source.write_text(after, encoding='utf-8', newline='\n')

if __name__ == '__main__':
    main()
