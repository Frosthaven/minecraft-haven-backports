package dev.frosthaven.havenbackports.creaking;

import net.minecraft.util.StringRepresentable;

/** 26.x CreakingHeartState (block state property creaking_heart_state). */
public enum CreakingHeartState implements StringRepresentable {
    UPROOTED("uprooted"),
    DORMANT("dormant"),
    AWAKE("awake");

    private final String name;

    CreakingHeartState(String name) {
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
