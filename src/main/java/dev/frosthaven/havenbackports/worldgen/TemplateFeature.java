package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** 26.x TemplateFeature: a structure template picked by weight, turned at random, centred on the origin. */
public class TemplateFeature extends Feature<TemplateFeature.Config> {
    public record Entry(ResourceLocation template, List<Rotation> rotations) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("id").forGetter(Entry::template),
                        Rotation.CODEC.listOf().optionalFieldOf("rotations", List.of(Rotation.values())).forGetter(Entry::rotations))
                .apply(i, Entry::new));
    }

    public record Config(SimpleWeightedRandomList<Entry> templates, Optional<Holder<StructureProcessorList>> processors)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                        SimpleWeightedRandomList.wrappedCodec(Entry.CODEC).fieldOf("templates").forGetter(Config::templates),
                        StructureProcessorType.LIST_CODEC.optionalFieldOf("processors").forGetter(Config::processors))
                .apply(i, Config::new));
    }

    public TemplateFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        var random = context.random();
        Entry entry = context.config().templates().getRandomValue(random).orElseThrow();
        Rotation rotation = Util.getRandom(entry.rotations(), random);
        StructureTemplate template = context.level().getLevel().getServer().getStructureManager().getOrCreate(entry.template());
        Vec3i offsetX = rotatedOffset(rotation, Direction.Axis.X, template);
        Vec3i offsetZ = rotatedOffset(rotation, Direction.Axis.Z, template);
        BlockPos pos = context.origin().offset(offsetX).offset(offsetZ);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRandom(random);
        context.config().processors().ifPresent(list -> {
            for (StructureProcessor p : list.value().list()) {
                settings.addProcessor(p);
            }
        });
        return template.placeInWorld(context.level(), pos, pos, settings, random, 3);
    }

    private static Vec3i rotatedOffset(Rotation rotation, Direction.Axis axis, StructureTemplate template) {
        return rotation.rotate(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)).getNormal().multiply(template.getSize().get(axis) / 2);
    }
}
