package dev.frosthaven.havenbackports.cube;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 26.x SulfurCube's hit angle helpers, line for line; 26.x Vec2.rotate (1.21.1's Vec2 has none) is rotate() here. */
final class CubeKnockback {
    private CubeKnockback() {}

    static Vec2 rotate(Vec2 v, double angleRadians) {
        float cosine = Mth.cos((float) angleRadians);
        float sine = Mth.sin((float) angleRadians);
        return new Vec2(v.x * cosine - v.y * sine, v.y * cosine + v.x * sine);
    }

    static Vec2 applyHorizontalHitAngleScale(float scale, Vec2 originalAngle, Vec3 attackerPosition, Vec3 aim, Vec3 targetCenter) {
        Vec3 toTarget = targetCenter.subtract(attackerPosition).normalize();
        float angleDiff = (float) Math.atan2(aim.x * toTarget.z - aim.z * toTarget.x, aim.x * toTarget.x + aim.z * toTarget.z);
        return rotate(originalAngle, angleDiff * scale);
    }

    static Vec2 applyVerticalHitAnglePowerTransfer(float scale, float horizontalPower, float verticalPower, Vec3 attackerPosition, Vec3 aim,
            Vec3 targetCentered, float targetHeight) {
        float half = 0.5F * targetHeight;
        Vec3 toTop = targetCentered.add(0.0, half, 0.0).subtract(attackerPosition).normalize();
        Vec3 toBottom = targetCentered.add(0.0, -half, 0.0).subtract(attackerPosition).normalize();
        float factor = (float) Mth.clampedMap(aim.y, toTop.y, toBottom.y, -1.0, 1.0);
        float ratio = Math.abs(factor * scale);
        if (factor < 0.0F) {
            ratio = -ratio;
        }
        return new Vec2(horizontalPower * (1.0F - ratio), verticalPower * (1.0F + ratio));
    }

    static Vec2 applyVerticalPositionAnglePowerRotation(float scale, float horizontalPower, float verticalPower, float originalHorizontal,
            float originalVertical, Vec3 attackerFeet, Vec3 targetFeet) {
        Vec3 d = targetFeet.subtract(attackerFeet);
        float angle = (float) Math.atan2(-d.y, d.horizontalDistance());
        Vec2 rotated = rotate(new Vec2(horizontalPower, verticalPower), -angle * scale);
        float h = originalHorizontal > 0.0F ? Mth.abs(rotated.x) / originalHorizontal : 0.0F;
        float v = originalVertical > 0.0F ? Mth.abs(rotated.y) / originalVertical : 0.0F;
        float max = Math.max(h, v);
        if (max > 1.0F) {
            rotated = rotated.scale(1.0F / max);
        }
        return rotated;
    }
}
