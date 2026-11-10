package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;
import net.minecraft.world.level.levelgen.blockpredicates.StateTestingPredicate;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 26.x MatchingFluidsPredicate with its fluid state check ("state"). 26.x fluid states carry a "source" property
 * that 1.21.1's lack: "source" is 1.21.1's FluidState.isSource(); other keys are the fluid state's own properties.
 */
public class MatchingFluidsPredicate extends StateTestingPredicate {
    public static final MapCodec<MatchingFluidsPredicate> CODEC = RecordCodecBuilder.mapCodec(i -> stateTestingCodec(i)
            .and(i.group(
                    RegistryCodecs.homogeneousList(Registries.FLUID).fieldOf("fluids").forGetter(c -> c.fluids),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("state", Map.of()).forGetter(c -> c.state)))
            .apply(i, MatchingFluidsPredicate::new));
    private final HolderSet<Fluid> fluids;
    private final Map<String, String> state;

    public MatchingFluidsPredicate(Vec3i offset, HolderSet<Fluid> fluids, Map<String, String> state) {
        super(offset);
        this.fluids = fluids;
        this.state = state;
    }

    @Override
    protected boolean test(BlockState blockState) {
        FluidState fluid = blockState.getFluidState();
        if (!fluid.is(this.fluids)) {
            return false;
        }
        for (Map.Entry<String, String> e : this.state.entrySet()) {
            if (e.getKey().equals("source")) {
                if (fluid.isSource() != Boolean.parseBoolean(e.getValue())) {
                    return false;
                }
                continue;
            }
            Property<?> property = fluid.getType().getStateDefinition().getProperty(e.getKey());
            if (property == null || !name(property, fluid).equals(e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static <T extends Comparable<T>> String name(Property<T> property, FluidState fluid) {
        return property.getName(fluid.getValue(property));
    }

    @Override
    public BlockPredicateType<?> type() {
        return Worldgen.MATCHING_FLUIDS.get();
    }
}
