package thetadev.constructionwand.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thetadev.constructionwand.ConstructionWand;

import java.util.HashSet;
import java.util.Set;

public record PacketUndoBlocks(Set<BlockPos> undoBlocks) implements CustomPacketPayload
{
    public static final Type<PacketUndoBlocks> TYPE = new Type<>(ConstructionWand.loc("undo_blocks"));

    public static final StreamCodec<ByteBuf, PacketUndoBlocks> STREAM_CODEC =
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.collection(HashSet::new))
                    .map(PacketUndoBlocks::new, msg -> new HashSet<>(msg.undoBlocks()));

    public PacketUndoBlocks(Set<BlockPos> undoBlocks) {
        this.undoBlocks = new HashSet<>(undoBlocks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PacketUndoBlocks msg, final IPayloadContext ctx) {
        if(ConstructionWand.instance.renderBlockPreview == null) return;
        ConstructionWand.instance.renderBlockPreview.undoBlocks = msg.undoBlocks();
    }
}
