package dev.frosthaven.havenbackports;

import com.mojang.logging.LogUtils;
import dev.frosthaven.havenbackports.bundle.Bundles;
import dev.frosthaven.havenbackports.map.ExplorerMaps;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Content from newer Minecraft versions, backported to 1.21.1 from Mojang's own files (see README.md and
 * tools/backport.py). Merges with other mods' copies of the same block are declared in
 * META-INF/haven_consolidated/aliases.json and carried out by Haven Consolidated.
 */
@Mod(HavenBackports.ID)
public final class HavenBackports {
    public static final String ID = "haven_backports";
    public static final Logger LOG = LogUtils.getLogger();

    public HavenBackports(IEventBus modBus, ModContainer container) {
        Content.register(modBus);
        Gear.register(modBus);
        Chests.register(modBus);
        Rods.register(modBus);
        Soulshale.register();
        Discs.register();
        BannerPatterns.register();
        for (String id : ExplorerMaps.IDS) {
            Content.ITEM.put(id, Content.ITEMS.registerItem(id, ExplorerMaps.ExplorerMapItem::new,
                    ExplorerMaps.properties()));
        }
        ExplorerMaps.register(modBus);
        for (String id : Bundles.DYED) {
            Content.ITEM.put(id, Content.ITEMS.registerItem(id, Bundles::make, Bundles.properties()));
        }
        Bundles.register(modBus);
        modBus.addListener(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent.class, Tabs::build);
    }
}
