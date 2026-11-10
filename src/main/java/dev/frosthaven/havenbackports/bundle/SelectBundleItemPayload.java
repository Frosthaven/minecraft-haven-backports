package dev.frosthaven.havenbackports.bundle;

import dev.frosthaven.havenbackports.HavenBackports;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 26.x's ServerboundSelectBundleItemPacket(slotId, selectedItemIndex), as a NeoForge payload: the client scrolled a
 * bundle in a container slot; the server selects the same item (AbstractContainerMenu.setSelectedBundleItemIndex).
 */
public record SelectBundleItemPayload(int slotId, int selectedItemIndex) implements CustomPacketPayload {
    public static final Type<SelectBundleItemPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "select_bundle_item"));
    public static final StreamCodec<FriendlyByteBuf, SelectBundleItemPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectBundleItemPayload::slotId, ByteBufCodecs.VAR_INT, SelectBundleItemPayload::selectedItemIndex,
            SelectBundleItemPayload::new);

    @Override
    public Type<SelectBundleItemPayload> type() {
        return TYPE;
    }

    static void handle(SelectBundleItemPayload p, IPayloadContext context) {
        AbstractContainerMenu menu = context.player().containerMenu;
        if (p.slotId >= 0 && p.slotId < menu.slots.size()) {
            BundleLogic.toggleSelectedItem(menu.slots.get(p.slotId).getItem(), p.selectedItemIndex);
        }
    }
}
