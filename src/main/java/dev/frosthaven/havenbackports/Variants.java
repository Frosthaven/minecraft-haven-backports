package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.variant.FarmVariant;
import dev.frosthaven.havenbackports.variant.VariantEgg;
import dev.frosthaven.havenbackports.variant.VariantHolder;
import dev.frosthaven.havenbackports.variant.WolfSoundVariant;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Climate variants of pigs, cows and chickens and the wolf's sound variants, as 26.4's data registries (pig_variant,
 * cow_variant, chicken_variant, wolf_sound_variant; Mojang's files, under our namespace) and spawn rules define them:
 * a farm animal takes the variant of its biome when it spawns (26.x VariantUtils.selectVariantToSpawn, here on
 * NeoForge's FinalizeSpawnEvent), a baby takes one parent's, a wolf picks a random sound variant. The variants are kept
 * and synced on the vanilla mobs by small mixins (PigMixin, CowMixin, ChickenMixin, WolfMixin). Blue and brown eggs:
 * laid by cold and warm chickens (26.x chicken_lay), they hatch cold and warm chicks.
 */
public final class Variants {
    public static final ResourceKey<Registry<FarmVariant>> PIG = key("pig_variant"), COW = key("cow_variant"), CHICKEN = key("chicken_variant");
    public static final ResourceKey<Registry<WolfSoundVariant>> WOLF_SOUND = ResourceKey.createRegistryKey(id("wolf_sound_variant"));
    public static final ResourceKey<FarmVariant> COLD_CHICKEN = ResourceKey.create(CHICKEN, id("cold")), WARM_CHICKEN = ResourceKey.create(CHICKEN, id("warm"));
    public static final ResourceKey<WolfSoundVariant> CLASSIC = ResourceKey.create(WOLF_SOUND, id("classic"));

    static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS = DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, HavenBackports.ID);
    public static final EntityDataSerializer<Holder<FarmVariant>> PIG_SERIALIZER = EntityDataSerializer.forValueType(ByteBufCodecs.holderRegistry(PIG));
    public static final EntityDataSerializer<Holder<FarmVariant>> COW_SERIALIZER = EntityDataSerializer.forValueType(ByteBufCodecs.holderRegistry(COW));
    public static final EntityDataSerializer<Holder<FarmVariant>> CHICKEN_SERIALIZER = EntityDataSerializer.forValueType(ByteBufCodecs.holderRegistry(CHICKEN));
    public static final EntityDataSerializer<Holder<WolfSoundVariant>> WOLF_SOUND_SERIALIZER = EntityDataSerializer.forValueType(ByteBufCodecs.holderRegistry(WOLF_SOUND));

    public static DeferredItem<Item> BLUE_EGG, BROWN_EGG;

    private Variants() {}

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, path);
    }

    private static ResourceKey<Registry<FarmVariant>> key(String name) {
        return ResourceKey.createRegistryKey(id(name));
    }

    /** 26.x VariantUtils.getDefaultOrAny: the named variant (temperate, classic), else any. */
    public static <T> Holder<T> defaultOrAny(RegistryAccess access, ResourceKey<Registry<T>> registry, String name) {
        Registry<T> r = access.registryOrThrow(registry);
        return r.getHolder(ResourceKey.create(registry, id(name))).map(h -> (Holder<T>) h).or(() -> r.getAny().map(h -> (Holder<T>) h)).orElseThrow();
    }

    /** 26.x WolfSoundVariants.pickRandomSoundVariant. */
    public static Holder<WolfSoundVariant> randomWolfSound(RegistryAccess access, RandomSource random) {
        return access.registryOrThrow(WOLF_SOUND).getRandom(random).map(h -> (Holder<WolfSoundVariant>) h).orElseGet(() -> defaultOrAny(access, WOLF_SOUND, "classic"));
    }

    static void register(IEventBus modBus) {
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, e -> {
            e.dataPackRegistry(PIG, FarmVariant.CODEC, FarmVariant.NETWORK_CODEC);
            e.dataPackRegistry(COW, FarmVariant.CODEC, FarmVariant.NETWORK_CODEC);
            e.dataPackRegistry(CHICKEN, FarmVariant.CODEC, FarmVariant.NETWORK_CODEC);
            e.dataPackRegistry(WOLF_SOUND, WolfSoundVariant.CODEC, WolfSoundVariant.CODEC);
        });
        SERIALIZERS.register("pig_variant", () -> PIG_SERIALIZER);
        SERIALIZERS.register("cow_variant", () -> COW_SERIALIZER);
        SERIALIZERS.register("chicken_variant", () -> CHICKEN_SERIALIZER);
        SERIALIZERS.register("wolf_sound_variant", () -> WOLF_SOUND_SERIALIZER);
        SERIALIZERS.register(modBus);
        // Items.BLUE_EGG / BROWN_EGG: stacks of 16, as the egg
        BLUE_EGG = Content.ITEMS.register("blue_egg", () -> new VariantEgg(COLD_CHICKEN, new Item.Properties().stacksTo(16)));
        BROWN_EGG = Content.ITEMS.register("brown_egg", () -> new VariantEgg(WARM_CHICKEN, new Item.Properties().stacksTo(16)));
        Content.ITEM.put("blue_egg", BLUE_EGG);
        Content.ITEM.put("brown_egg", BROWN_EGG);
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(() -> List.of(BLUE_EGG, BROWN_EGG)
                .forEach(egg -> DispenserBlock.registerProjectileBehavior(egg.get()))));
        NeoForge.EVENT_BUS.addListener(Variants::spawn);
    }

    /** Mob.finalizeSpawn in 26.x Pig, Cow, Chicken (biome variant) and Wolf (random sound variant). */
    @SuppressWarnings("unchecked")
    private static void spawn(FinalizeSpawnEvent e) {
        Mob mob = e.getEntity();
        ResourceKey<Registry<FarmVariant>> registry = mob instanceof Pig ? PIG : mob instanceof Cow && !(mob instanceof MushroomCow) ? COW
                : mob instanceof Chicken ? CHICKEN : null;
        if (registry != null) {
            FarmVariant.select(e.getLevel(), mob.blockPosition(), registry, e.getLevel().getRandom())
                    .ifPresent(v -> ((VariantHolder<FarmVariant>) mob).haven_backports$setVariant(v));
        } else if (mob instanceof Wolf) {
            ((VariantHolder<WolfSoundVariant>) mob).haven_backports$setVariant(randomWolfSound(e.getLevel().registryAccess(), e.getLevel().getRandom()));
        }
    }
}
