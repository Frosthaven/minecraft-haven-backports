package dev.frosthaven.havenbackports.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Mojang's 26.x {@code SideChainPart} (BlockStateProperties.SIDE_CHAIN_PART, property name "side_chain"). */
public enum SideChainPart implements StringRepresentable {
    UNCONNECTED("unconnected"),
    RIGHT("right"),
    CENTER("center"),
    LEFT("left");

    public static final EnumProperty<SideChainPart> PROPERTY = EnumProperty.create("side_chain", SideChainPart.class);

    private final String name;

    SideChainPart(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public boolean isConnected() {
        return this != UNCONNECTED;
    }

    public boolean isConnectionTowards(SideChainPart endPart) {
        return this == CENTER || this == endPart;
    }

    public boolean isChainEnd() {
        return this != CENTER;
    }

    public SideChainPart whenConnectedToTheRight() {
        return switch (this) {
            case UNCONNECTED, LEFT -> LEFT;
            case RIGHT, CENTER -> CENTER;
        };
    }

    public SideChainPart whenConnectedToTheLeft() {
        return switch (this) {
            case UNCONNECTED, RIGHT -> RIGHT;
            case CENTER, LEFT -> CENTER;
        };
    }

    public SideChainPart whenDisconnectedFromTheRight() {
        return switch (this) {
            case UNCONNECTED, LEFT -> UNCONNECTED;
            case RIGHT, CENTER -> RIGHT;
        };
    }

    public SideChainPart whenDisconnectedFromTheLeft() {
        return switch (this) {
            case UNCONNECTED, RIGHT -> UNCONNECTED;
            case CENTER, LEFT -> LEFT;
        };
    }
}
