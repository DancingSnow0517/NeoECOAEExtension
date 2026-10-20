"""Convert the retained wire payloads to the project's actual Forge transport contract."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

def main():
    for source in (ROOT / 'src').rglob('*.java'):
        before = source.read_text(encoding='utf-8')
        after = before
        if 'RegistryFriendlyByteBuf' in after:
            after = after.replace('RegistryFriendlyByteBuf', 'FriendlyByteBuf')
            after = after.replace('new FriendlyByteBuf(Unpooled.buffer(256, MenuStreamAssembler.MAX_BYTES), player.registryAccess())',
                                  'new FriendlyByteBuf(Unpooled.buffer(256, MenuStreamAssembler.MAX_BYTES))')
            after = after.replace('new FriendlyByteBuf(Unpooled.wrappedBuffer(complete), player.registryAccess())',
                                  'new FriendlyByteBuf(Unpooled.wrappedBuffer(complete))')
            after = after.replace('new FriendlyByteBuf(Unpooled.buffer(256, MAX_UNCOMPRESSED_BYTES), data.registryAccess())',
                                  'new FriendlyByteBuf(Unpooled.buffer(256, MAX_UNCOMPRESSED_BYTES))')
            after = after.replace('new FriendlyByteBuf(Unpooled.wrappedBuffer(uncompressed), data.registryAccess())',
                                  'new FriendlyByteBuf(Unpooled.wrappedBuffer(uncompressed))')
        if source.parent.name == 'network' and source.name.startswith('ECO') and 'implements CustomPacketPayload' in after:
            after = after.replace('import net.minecraft.network.protocol.common.custom.CustomPacketPayload;\n', '')
            after = after.replace('import net.minecraftforge.network.handling.IPayloadContext;',
                                  'import cn.dancingsnow.neoecoae.platform.network.NEPacketContext;')
            after = after.replace('IPayloadContext', 'NEPacketContext')
            after = after.replace(' implements CustomPacketPayload', '')
            after = after.replace('\n        implements CustomPacketPayload', '')
            after = re.sub(r'    public static final Type<[^;]+;', '', after)
            after = re.sub(r'    @Override public Type<[^\n]+\n', '', after)
            after = after.replace('import net.minecraft.network.codec.StreamCodec;',
                                  'import cn.dancingsnow.neoecoae.platform.network.NEStreamCodec;')
            after = after.replace('StreamCodec', 'NEStreamCodec').replace('NENEStreamCodec', 'NEStreamCodec')
        if 'PacketDistributor.sendToServer' in after or 'PacketDistributor.sendToPlayer' in after:
            after = after.replace('net.minecraftforge.network.PacketDistributor.sendToPlayer',
                                  'cn.dancingsnow.neoecoae.network.ECONetwork.sendToPlayer')
            after = after.replace('import net.minecraftforge.network.PacketDistributor;',
                                  'import cn.dancingsnow.neoecoae.network.ECONetwork;')
            after = after.replace('PacketDistributor.sendToServer', 'ECONetwork.sendToServer')
            after = after.replace('PacketDistributor.sendToPlayer', 'ECONetwork.sendToPlayer')
        if before != after:
            source.write_text(after, encoding='utf-8', newline='\n')

if __name__ == '__main__':
    main()
