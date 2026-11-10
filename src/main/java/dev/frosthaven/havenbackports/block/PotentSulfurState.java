package dev.frosthaven.havenbackports.block;

import net.minecraft.util.StringRepresentable;

/** 26.x PotentSulfurState (block state property potent_sulfur_state). */
public enum PotentSulfurState implements StringRepresentable {
    DRY("dry"),
    WET("wet"),
    DORMANT("dormant"),
    ERUPTING("erupting"),
    CONTINUOUS("continuous");

    private final String name;

    PotentSulfurState(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
