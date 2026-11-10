package dev.frosthaven.havenbackports;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;

/**
 * Music discs as 26.4's Items defines them: stacksTo(1), rarity, jukeboxPlayable(the song). The songs (length,
 * comparator output, description) are Mojang's jukebox_song files and the sound events Mojang's sounds.json entries
 * (tools/extras.py); the sound event ids are ours, as 1.21.1 has no music_disc.bounce etc.
 */
final class Discs {
    private Discs() {}

    static void register() {
        Content.sound("music_disc.tears");
        Content.sound("music_disc.lava_chicken");
        Content.sound("music_disc.bounce");
        disc("music_disc_tears", "tears", Rarity.UNCOMMON);
        disc("music_disc_lava_chicken", "lava_chicken", Rarity.RARE);
        disc("music_disc_bounce", "bounce", Rarity.UNCOMMON);
    }

    private static void disc(String id, String song, Rarity rarity) {
        ResourceKey<JukeboxSong> key = ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, song));
        Content.ITEM.put(id, Content.ITEMS.registerItem(id, Item::new, new Item.Properties().stacksTo(1).rarity(rarity).jukeboxPlayable(key)));
    }
}
