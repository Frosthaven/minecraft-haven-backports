package dev.frosthaven.havenbackports;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

/**
 * 26.x wandering trader offers that give our items, from data/haven_backports/wandering_trades.json (tools/trades.py:
 * 26.x common -> 1.21.1 generic pool, uncommon -> rare). reputation_discount is 1.21.1's price multiplier.
 */
final class Trades {
    private Trades() {}

    static void wanderer(WandererTradesEvent e) {
        try (InputStream in = Trades.class.getResourceAsStream("/data/" + HavenBackports.ID + "/wandering_trades.json")) {
            JsonArray trades = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonArray();
            for (JsonElement el : trades) {
                JsonObject t = el.getAsJsonObject();
                JsonObject wants = t.getAsJsonObject("wants");
                JsonObject gives = t.getAsJsonObject("gives");
                Item want = BuiltInRegistries.ITEM.get(ResourceLocation.parse(wants.get("id").getAsString()));
                Item give = BuiltInRegistries.ITEM.get(ResourceLocation.parse(gives.get("id").getAsString()));
                int wantCount = wants.get("count").getAsInt();
                int giveCount = gives.get("count").getAsInt();
                int maxUses = t.get("max_uses").getAsInt();
                int xp = t.get("xp").getAsInt();
                float discount = t.get("reputation_discount").getAsFloat();
                VillagerTrades.ItemListing listing = (trader, random) ->
                        new MerchantOffer(new ItemCost(want, wantCount), new ItemStack(give, giveCount), maxUses, xp, discount);
                (t.get("pool").getAsString().equals("rare") ? e.getRareTrades() : e.getGenericTrades()).add(listing);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("wandering_trades.json", ex);
        }
    }
}
