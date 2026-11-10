package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.block.StrawBedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The straw bed (26.4's Blocks.STRAW_BED: of().mapColor(COLOR_YELLOW).sound(STRAW_BED).strength(0.2).noOcclusion()
 * .ignitedByLava().pushReaction(POPPED); flammable 30/60; 4 from 3 hay bales; item stacks to 16, composts 0.85) and
 * its overworld straw_bed_rule through NeoForge events: it never sets the spawn point (PlayerSetSpawnEvent) and breaks
 * when you get up (PlayerWakeUpEvent; after the player has stood up, as 26.x's stopSleeping does it).
 */
public final class StrawBeds {
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK_LEAVE = Content.sound("block.straw_bed.break_leave");
    static final DeferredRegister<ResourceLocation> STATS = DeferredRegister.create(Registries.CUSTOM_STAT, HavenBackports.ID);
    /** Stats.SLEEP_IN_STRAW_BED, counted in place of sleep_in_bed (ServerPlayerMixin). */
    public static final DeferredHolder<ResourceLocation, ResourceLocation> SLEEP_IN_STRAW_BED =
            STATS.register("sleep_in_straw_bed", () -> ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "sleep_in_straw_bed"));
    public static DeferredBlock<Block> STRAW_BED;

    private StrawBeds() {}

    static void register(IEventBus modBus) {
        DeferredSoundType sound = new DeferredSoundType(1.0F, 1.0F, Content.sound("block.straw_bed.break"), Content.sound("block.straw_bed.step"),
                Content.sound("block.straw_bed.place"), Content.sound("block.straw_bed.hit"), Content.sound("block.straw_bed.fall"));
        STRAW_BED = Content.BLOCKS.register("straw_bed", () -> new StrawBedBlock(Properties.of().mapColor(MapColor.COLOR_YELLOW).sound(sound)
                .strength(0.2F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY)));
        Content.ITEM.put("straw_bed", Content.ITEMS.register("straw_bed", () -> new BlockItem(STRAW_BED.get(), new Item.Properties().stacksTo(16))));
        Content.FLAMMABLE.add(() -> ((net.minecraft.world.level.block.FireBlock) net.minecraft.world.level.block.Blocks.FIRE).setFlammable(STRAW_BED.get(), 30, 60));
        STATS.register(modBus);
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(() -> Stats.CUSTOM.get(SLEEP_IN_STRAW_BED.get(), StatFormatter.DEFAULT)));
        NeoForge.EVENT_BUS.addListener(StrawBeds::keepSpawn);
        NeoForge.EVENT_BUS.addListener(StrawBeds::wakeUp);
    }

    /** straw_bed_rule can_set_spawn never. */
    private static void keepSpawn(net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent e) {
        BlockPos pos = e.getNewSpawn();
        if (pos != null && !e.isForced() && e.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.server.getLevel(e.getSpawnLevel());
            if (level != null && level.getBlockState(pos).getBlock() instanceof StrawBedBlock) {
                e.setCanceled(true);
            }
        }
    }

    /** straw_bed_rule destroy_on_leave (26.x AbstractBedBlock.onStopSleeping). */
    private static void wakeUp(PlayerWakeUpEvent e) {
        if (e.getEntity() instanceof ServerPlayer player) {
            player.getSleepingPos().ifPresent(pos -> {
                ServerLevel level = player.serverLevel();
                if (level.getBlockState(pos).getBlock() instanceof StrawBedBlock bed && level.dimensionType().bedWorks()) {
                    MinecraftServer server = level.getServer();
                    server.tell(new TickTask(server.getTickCount(), () -> {
                        if (level.getBlockState(pos).getBlock() instanceof StrawBedBlock) {
                            bed.destroyBed(level, pos);
                        }
                    }));
                }
            });
        }
    }
}
