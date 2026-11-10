package dev.frosthaven.havenbackports.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x's ExplorationMapFunction (minecraft:exploration_map) as haven_backports:exploration_map. 26.x writes the map
 * into the item it is given (each explorer map is its own item); 1.21.1's function only turns minecraft:map into
 * minecraft:filled_map. Same fields and defaults (decoration mansion, zoom 2, search radius 50, skip existing
 * chunks); the destination is a #structure tag, as every Mojang file names it (1.21.1's findNearestMapStructure
 * takes a tag). An item it cannot fill keeps no map id, for haven_backports:filtered to discard.
 */
public final class ExplorationMapFunction extends LootItemConditionalFunction {
    public static final MapCodec<ExplorationMapFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).and(i.group(
            TagKey.hashedCodec(Registries.STRUCTURE).fieldOf("destination").forGetter(f -> f.destination),
            MapDecorationType.CODEC.optionalFieldOf("decoration", MapDecorationTypes.WOODLAND_MANSION).forGetter(f -> f.decoration),
            Codec.BYTE.optionalFieldOf("zoom", (byte) 2).forGetter(f -> f.zoom),
            Codec.INT.optionalFieldOf("search_radius", 50).forGetter(f -> f.searchRadius),
            Codec.BOOL.optionalFieldOf("skip_existing_chunks", true).forGetter(f -> f.skipKnownStructures)
    )).apply(i, ExplorationMapFunction::new));

    private final TagKey<Structure> destination;
    private final Holder<MapDecorationType> decoration;
    private final byte zoom;
    private final int searchRadius;
    private final boolean skipKnownStructures;

    private ExplorationMapFunction(List<LootItemCondition> conditions, TagKey<Structure> destination, Holder<MapDecorationType> decoration,
                                   byte zoom, int searchRadius, boolean skipKnownStructures) {
        super(conditions);
        this.destination = destination;
        this.decoration = decoration;
        this.zoom = zoom;
        this.searchRadius = searchRadius;
        this.skipKnownStructures = skipKnownStructures;
    }

    @Override
    public LootItemFunctionType<ExplorationMapFunction> getType() {
        return ExplorerMaps.EXPLORATION_MAP.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ORIGIN);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (stack.isEmpty()) {
            return stack;
        }
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) {
            return stack;
        }
        ServerLevel level = context.getLevel();
        BlockPos pos = BlockPos.containing(origin);
        if (this.skipKnownStructures) {
            this.claimStructureAt(level, pos);
        }
        BlockPos found = level.findNearestMapStructure(this.destination, pos, this.searchRadius, this.skipKnownStructures);
        if (found == null) {
            return stack;
        }
        // MapItem.applyNewSavedData (26.x): 1.21.1 makes the saved data through MapItem.create.
        stack.set(net.minecraft.core.component.DataComponents.MAP_ID,
                MapItem.create(level, found.getX(), found.getZ(), this.zoom, true, true).get(net.minecraft.core.component.DataComponents.MAP_ID));
        MapItem.renderBiomePreviewMap(level, stack);
        MapItemSavedData.addTargetDecoration(stack, found, "+", this.decoration);
        return stack;
    }

    /** 26.x claimStructureAt: the structure the loot comes from is referenced, so the map leads elsewhere. */
    private void claimStructureAt(ServerLevel level, BlockPos pos) {
        StructureManager manager = level.structureManager();
        for (Holder<Structure> structure : level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTagOrEmpty(this.destination)) {
            StructureStart start = manager.getStructureAt(pos, structure.value());
            if (start.isValid() && start.canBeReferenced()) {
                manager.addReference(start);
                return;
            }
        }
    }
}
