package dev.frosthaven.havenbackports.net;

import dev.frosthaven.havenbackports.HavenBackports;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 26.x level events 1.21.1 lacks, sent as the vanilla level event packet is (players within 64 blocks). Only 2014,
 *  PARTICLES_DESTROY_BLOCK: the block's break particles without its break sound (26.x brittle ice cracking). */
public record LevelEvent26(int event, BlockPos pos, int data) implements CustomPacketPayload {
    public static final int PARTICLES_DESTROY_BLOCK = 2014;
    public static final Type<LevelEvent26> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "level_event"));
    public static final StreamCodec<ByteBuf, LevelEvent26> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, LevelEvent26::event,
            BlockPos.STREAM_CODEC, LevelEvent26::pos, ByteBufCodecs.VAR_INT, LevelEvent26::data, LevelEvent26::new);

    @Override
    public Type<LevelEvent26> type() {
        return TYPE;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("1").playToClient(TYPE, STREAM_CODEC, LevelEvent26::handle));
    }

    public static void destroyBlockParticles(ServerLevel level, BlockPos pos, BlockState state) {
        PacketDistributor.sendToPlayersNear(level, null, pos.getX(), pos.getY(), pos.getZ(), 64.0,
                new LevelEvent26(PARTICLES_DESTROY_BLOCK, pos, Block.getId(state)));
    }

    private static void handle(LevelEvent26 event, IPayloadContext context) {
        if (event.event() == PARTICLES_DESTROY_BLOCK) {
            Client.destroyBlockParticles(event.pos(), event.data());
        }
    }

    /** Client-only code, loaded only when a packet arrives on the client. */
    private static final class Client {
        static void destroyBlockParticles(BlockPos pos, int stateId) {
            if (Minecraft.getInstance().level != null) {
                Minecraft.getInstance().level.addDestroyBlockEffect(pos, Block.stateById(stateId));
            }
        }
    }
}
