"""One-time exact API name mappings verified against the pinned 1.20.1 dependency JARs."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPLACEMENTS = {
    'appeng.blockentity.grid.AENetworkedBlockEntity': 'appeng.blockentity.grid.AENetworkBlockEntity',
    'AENetworkedBlockEntity': 'AENetworkBlockEntity',
    'AENetworkedPoweredBlockEntity': 'AENetworkPowerBlockEntity',
    'appeng.api.config.PowerUnit;': 'appeng.api.config.PowerUnits;',
    'PowerUnit.': 'PowerUnits.',
    'PowerUnit externalUnit': 'PowerUnits externalUnit',
    'MenuHostLocator': 'MenuLocator',
    'net.minecraft.world.level.chunk.status.ChunkStatus': 'net.minecraft.world.level.chunk.ChunkStatus',
    'com.lowdragmc.lowdraglib2.syncdata.annotation.': 'com.lowdragmc.lowdraglib.syncdata.annotation.',
    'com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage': 'com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage',
    'com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage': 'com.lowdragmc.lowdraglib.syncdata.IManagedStorage',
    'com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender': 'com.lowdragmc.lowdraglib.syncdata.rpc.RPCSender',
    'com.lowdragmc.lowdraglib2.syncdata.IContentChangeAware': 'com.lowdragmc.lowdraglib.syncdata.IContentChangeAware',
    'com.lowdragmc.lowdraglib2.async.AsyncThreadData': 'com.lowdragmc.lowdraglib.async.AsyncThreadData',
    'com.lowdragmc.lowdraglib2.utils.data.BlockInfo': 'com.lowdragmc.lowdraglib.utils.BlockInfo',
    'com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld': 'com.lowdragmc.lowdraglib.utils.TrackedDummyWorld',
    'net.minecraftforge.fml.common.EventBusSubscriber': 'net.minecraftforge.fml.common.Mod.EventBusSubscriber',
    'net.neoforged.neoforgespi.language.ModFileScanData': 'net.minecraftforge.forgespi.language.ModFileScanData',
    'net.neoforged.neoforgespi.locating.IModFile': 'net.minecraftforge.forgespi.locating.IModFile',
    'net.minecraftforge.registries.NeoForgeRegistries': 'net.minecraftforge.registries.ForgeRegistries',
    'NeoForgeRegistries.Keys.': 'ForgeRegistries.Keys.',
    'com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity': 'cn.dancingsnow.neoecoae.platform.sync.NEManagedBlockEntity',
    'ISyncPersistRPCBlockEntity': 'NEManagedBlockEntity',
}

def main():
    changed = 0
    for source in (ROOT / 'src').rglob('*.java'):
        before = source.read_text(encoding='utf-8')
        after = before
        for old, new in REPLACEMENTS.items():
            after = after.replace(old, new)
        if after != before:
            source.write_text(after, encoding='utf-8', newline='\n')
            changed += 1
    print(f'Updated {changed} Java sources. This maps names, not all API semantics.')

if __name__ == '__main__':
    main()
