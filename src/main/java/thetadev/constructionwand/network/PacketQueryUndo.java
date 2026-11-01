package thetadev.constructionwand.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thetadev.constructionwand.ConstructionWand;

public record PacketQueryUndo(boolean undoPressed) implements CustomPacketPayload
{
    public static final Type<PacketQueryUndo> TYPE = new Type<>(ConstructionWand.loc("query_undo"));

    public static final StreamCodec<ByteBuf, PacketQueryUndo> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(PacketQueryUndo::new, PacketQueryUndo::undoPressed);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PacketQueryUndo msg, final IPayloadContext ctx) {
        if(!(ctx.player() instanceof ServerPlayer player)) return;

        ConstructionWand.instance.undoHistory.updateClient(player, msg.undoPressed());
    }
}
