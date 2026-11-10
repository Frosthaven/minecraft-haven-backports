package dev.frosthaven.havenbackports.frostbite;

import dev.frosthaven.havenbackports.Content;
import dev.frosthaven.havenbackports.HavenBackports;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The frostbite, the ice ball, the freezing effect and its potions, and the music disc Flurry (26.x EntityTypes.FROSTBITE
 * and ICE_BALL, Items.ICE_BALL, FROSTBITE_SPAWN_EGG and MUSIC_DISC_FLURRY, MobEffects.FREEZING, Potions.FREEZING and
 * LONG_FREEZING, ParticleTypes.FREEZING, JukeboxSongs.FLURRY, their SoundEvents), and what 26.x code elsewhere does
 * for them: DefaultAttributes, SpawnPlacements (the frostbite and the stray share Monster.checkColdVariantSpawnRules),
 * VanillaBrewingProvider, DispenseItemBehavior, and LivingEntity.canBeAffected (no freezing effect for a mob 26.x tags
 * #immune_to_freezing; 1.21.1's name for that tag is #freeze_immune_entity_types). Ids are Mojang's, in our namespace.
 */
public final class FrostbiteContent {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, HavenBackports.ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, HavenBackports.ID);
    private static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, HavenBackports.ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTBITE_AMBIENT = Content.sound("entity.frostbite.ambient"),
            FROSTBITE_DEATH = Content.sound("entity.frostbite.death"), FROSTBITE_HURT = Content.sound("entity.frostbite.hurt"),
            FROSTBITE_STEP = Content.sound("entity.frostbite.step"),
            ZOMBIE_CONVERTED_TO_FROSTBITE = Content.sound("entity.zombie.converted_to_frostbite"),
            FROSTBITE_CONVERTED_TO_ZOMBIE = Content.sound("entity.frostbite.converted_to_zombie"),
            DROWNED_CONVERTED_TO_ZOMBIE = Content.sound("entity.drowned.converted_to_zombie"),
            ICE_BALL_BREAK = Content.sound("entity.ice_ball.break"), ICE_BALL_THROW = Content.sound("entity.ice_ball.throw"),
            APPLY_EFFECT_FREEZING = Content.sound("event.mob_effect.freezing"),
            PARROT_IMITATE_FROSTBITE = Content.sound("entity.parrot.imitate.frostbite"),
            MUSIC_DISC_FLURRY = Content.sound("music_disc.flurry");

    /** 26.x ParticleTypes.FREEZING (FreezingParticle on the client): the freezing effect's ambient particle. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FREEZING_PARTICLE = Content.PARTICLES.register("freezing",
            () -> new SimpleParticleType(false));

    /** FROSTBITE: of(Frostbite::new, MONSTER).sized(0.6, 1.95).eyeHeight(1.74).passengerAttachments(2.0125)
     *  .ridingOffset(-0.7).clientTrackingRange(8).notInPeaceful() (1.21.1's Monster already leaves in peaceful). */
    public static final DeferredHolder<EntityType<?>, EntityType<Frostbite>> FROSTBITE = ENTITIES.register("frostbite",
            () -> EntityType.Builder.<Frostbite>of(Frostbite::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F)
                    .passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8).build(HavenBackports.ID + ":frostbite"));
    /** ICE_BALL: of(IceBall::new, MISC).noLootTable().sized(0.25, 0.25).clientTrackingRange(4).updateInterval(10)
     *  (1.21.1's builder has no noLootTable: a projectile never drops loot there either). */
    public static final DeferredHolder<EntityType<?>, EntityType<IceBall>> ICE_BALL = ENTITIES.register("ice_ball",
            () -> EntityType.Builder.<IceBall>of(IceBall::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4)
                    .updateInterval(10).build(HavenBackports.ID + ":ice_ball"));

    /** MobEffects.FREEZING: FreezingMobEffect(HARMFUL, 10415854).withSoundOnAdded(APPLY_EFFECT_FREEZING). */
    public static final DeferredHolder<MobEffect, MobEffect> FREEZING = EFFECTS.register("freezing",
            () -> new FreezingMobEffect(MobEffectCategory.HARMFUL, 10415854).withSoundOnAdded(APPLY_EFFECT_FREEZING.get()));
    /** Potions.FREEZING (900 ticks) and LONG_FREEZING (1800), both named "freezing". */
    public static final DeferredHolder<Potion, Potion> FREEZING_POTION = POTIONS.register("freezing",
            () -> new Potion("freezing", new MobEffectInstance(FREEZING, 900)));
    public static final DeferredHolder<Potion, Potion> LONG_FREEZING_POTION = POTIONS.register("long_freezing",
            () -> new Potion("freezing", new MobEffectInstance(FREEZING, 1800)));

    /** JukeboxSongs.FLURRY: data (data/haven_backports/jukebox_song/flurry.json: MUSIC_DISC_FLURRY, 154 s, comparator 7). */
    public static final ResourceKey<JukeboxSong> FLURRY = ResourceKey.create(Registries.JUKEBOX_SONG,
            ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "flurry"));

    /** Items.ICE_BALL: IceBallItem, stacksTo(16). */
    public static final DeferredItem<Item> ICE_BALL_ITEM = Content.ITEMS.register("ice_ball",
            () -> new IceBallItem(new Item.Properties().stacksTo(16)));
    /** Items.FROSTBITE_SPAWN_EGG (registerSpawnEgg): 26.x's own item model, no tints. */
    public static final DeferredItem<Item> FROSTBITE_SPAWN_EGG = Content.ITEMS.register("frostbite_spawn_egg",
            () -> new DeferredSpawnEggItem(FROSTBITE, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));
    /** Items.MUSIC_DISC_FLURRY: stacksTo(1).rarity(UNCOMMON).jukeboxPlayable(FLURRY). */
    public static final DeferredItem<Item> MUSIC_DISC_FLURRY_ITEM = Content.ITEMS.register("music_disc_flurry",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).jukeboxPlayable(FLURRY)));

    /** 26.x ItemTags.FROSTBITE_PREFERRED_WEAPONS (Mob.compareWeapons). */
    static final TagKey<Item> FROSTBITE_PREFERRED_WEAPONS = TagKey.create(Registries.ITEM,
            ResourceLocation.withDefaultNamespace("frostbite_preferred_weapons"));

    private FrostbiteContent() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        POTIONS.register(modBus);
        Content.ITEM.put("ice_ball", ICE_BALL_ITEM);
        Content.ITEM.put("frostbite_spawn_egg", FROSTBITE_SPAWN_EGG);
        Content.ITEM.put("music_disc_flurry", MUSIC_DISC_FLURRY_ITEM);
        modBus.addListener(EntityAttributeCreationEvent.class, e -> e.put(FROSTBITE.get(), Frostbite.createAttributes().build()));
        modBus.addListener(RegisterSpawnPlacementsEvent.class, FrostbiteContent::spawnPlacements);
        // DispenseItemBehavior: DispenserBlock.registerProjectileBehavior(Items.ICE_BALL)
        Content.COMMON_SETUP.add(() -> DispenserBlock.registerProjectileBehavior(ICE_BALL_ITEM.get()));
        NeoForge.EVENT_BUS.addListener(RegisterBrewingRecipesEvent.class, FrostbiteContent::brewing);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Applicable.class, FrostbiteContent::immuneToFreezing);
    }

    /** SpawnPlacements: FROSTBITE and STRAY, ON_GROUND, MOTION_BLOCKING_NO_LEAVES, Monster::checkColdVariantSpawnRules.
     *  The stray's rule is 26.x's too (it spawns in the ice caves, under the sky check it had in 1.21.1). */
    private static void spawnPlacements(RegisterSpawnPlacementsEvent e) {
        e.register(FROSTBITE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ColdVariantSpawn::check, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(EntityType.STRAY, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ColdVariantSpawn::check, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** VanillaBrewingProvider: buildStartMix(ICE_BALL, FREEZING) (water + ice ball = mundane, awkward + ice ball =
     *  freezing) and buildMix(FREEZING, REDSTONE, LONG_FREEZING); 1.21.1's container mixes add splash and lingering. */
    private static void brewing(RegisterBrewingRecipesEvent e) {
        e.getBuilder().addStartMix(ICE_BALL_ITEM.get(), FREEZING_POTION);
        e.getBuilder().addMix(FREEZING_POTION, Items.REDSTONE, LONG_FREEZING_POTION);
    }

    /** 26.x LivingEntity.canBeAffected: no FREEZING for #immune_to_freezing (1.21.1: #freeze_immune_entity_types). */
    private static void immuneToFreezing(MobEffectEvent.Applicable e) {
        if (e.getEffectInstance().is(FREEZING) && e.getEntity().getType().is(net.minecraft.tags.EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)) {
            e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    static boolean isFreezing(Holder<MobEffect> effect) {
        return effect.is(FREEZING);
    }
}
