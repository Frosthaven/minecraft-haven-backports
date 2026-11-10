package dev.frosthaven.havenbackports.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.frosthaven.havenbackports.HavenBackports;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/**
 * The cartographer's trades as 26.4 defines them (data/minecraft/trade_set/cartographer, read by tools/maps.py into
 * data/haven_backports/villager_trades/cartographer.json). 26.x's AbstractVillager picks 2 trades per level, without
 * duplicates, dropping a trade whose merchant_predicate fails or whose item modifier empties the item; 1.21.1's
 * addOffersFromItemListings picks 2 the same way when a listing returns null, so each 26.x trade is one listing:
 * null for the wrong villager type, null when its explorer map finds no structure (26.x's filtered + discard).
 * Runs at the highest priority, so the level lists hold only vanilla's trades when they are replaced and other
 * mods' cartographer trades are added after ours. 1.21.1's experimental trade rebalance keeps its own lists.
 */
final class CartographerTrades {
    private static final String TABLE = "/data/" + HavenBackports.ID + "/villager_trades/cartographer.json";

    private CartographerTrades() {}

    static void replace(VillagerTradesEvent e) {
        if (e.getType() != VillagerProfession.CARTOGRAPHER) {
            return;
        }
        JsonObject levels = read().getAsJsonObject("levels");
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, e.getRegistryAccess());
        for (int level = 1; level <= 5; level++) {
            List<VillagerTrades.ItemListing> list = e.getTrades().get(level);
            list.clear();
            for (JsonElement t : levels.getAsJsonArray(String.valueOf(level))) {
                list.add(Trade.of(t.getAsJsonObject(), ops));
            }
        }
    }

    private static JsonObject read() {
        try (Reader r = new InputStreamReader(CartographerTrades.class.getResourceAsStream(TABLE), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalStateException("Haven Backports: cannot read " + TABLE, ex);
        }
    }

    /** One 26.x VillagerTrade with fixed counts (tools/maps.py refuses anything else). */
    private record Trade(Item wants, int wantsCount, Optional<Item> extra, int extraCount, ItemStack gives, int maxUses, int xp,
                         float discount, Set<ResourceLocation> villagerTypes, List<LootItemFunction> modifiers)
            implements VillagerTrades.ItemListing {

        static Trade of(JsonObject t, RegistryOps<JsonElement> ops) {
            JsonObject wants = t.getAsJsonObject("wants");
            Optional<JsonObject> extra = Optional.ofNullable(t.getAsJsonObject("additional_wants"));
            JsonObject gives = t.getAsJsonObject("gives");
            Set<ResourceLocation> types = null;
            if (t.has("villager_types")) {
                types = new HashSet<>();
                for (JsonElement v : t.getAsJsonArray("villager_types")) {
                    types.add(ResourceLocation.parse(v.getAsString()));
                }
            }
            List<LootItemFunction> modifiers = new ArrayList<>();
            if (t.has("given_item_modifier")) {
                for (JsonElement f : t.getAsJsonArray("given_item_modifier")) {
                    modifiers.add(LootItemFunctions.ROOT_CODEC.parse(ops, f).getOrThrow(
                            msg -> new IllegalStateException("cartographer trade " + t.get("id") + ": " + msg)));
                }
            }
            return new Trade(item(wants), wants.get("count").getAsInt(), extra.map(Trade::item), extra.map(x -> x.get("count").getAsInt()).orElse(0),
                    new ItemStack(item(gives), gives.get("count").getAsInt()), t.get("max_uses").getAsInt(), t.get("xp").getAsInt(),
                    t.get("reputation_discount").getAsFloat(), types, List.copyOf(modifiers));
        }

        private static Item item(JsonObject cost) {
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(cost.get("id").getAsString()));
        }

        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            if (this.villagerTypes != null && !(trader instanceof VillagerDataHolder holder
                    && this.villagerTypes.contains(BuiltInRegistries.VILLAGER_TYPE.getKey(holder.getVillagerData().getType())))) {
                return null;
            }
            ItemStack result = this.gives.copy();
            if (!this.modifiers.isEmpty()) {
                if (!(trader.level() instanceof ServerLevel level)) {
                    return null;
                }
                // 26.x's villager_trade context: the villager is the loot origin and "this" entity.
                LootContext context = new LootContext.Builder(new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, trader.position())
                        .withParameter(LootContextParams.THIS_ENTITY, trader)
                        .create(LootContextParamSets.CHEST)).create(Optional.empty());
                for (LootItemFunction f : this.modifiers) {
                    result = f.apply(result, context);
                }
                if (result.isEmpty()) {
                    return null;
                }
            }
            return new MerchantOffer(new ItemCost(this.wants, this.wantsCount), this.extra.map(i -> new ItemCost(i, this.extraCount)),
                    result, this.maxUses, this.xp, this.discount);
        }
    }
}
