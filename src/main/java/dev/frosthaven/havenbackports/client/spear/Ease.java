package dev.frosthaven.havenbackports.client.spear;

import net.minecraft.util.Mth;

/** The 26.x {@code net.minecraft.util.Ease} functions the spear animations use, verbatim (1.21.1 has no Ease). */
final class Ease {
    private Ease() {}

    static float inQuad(float x) {
        return x * x;
    }

    static float outQuart(float x) {
        return 1.0F - Mth.square(Mth.square(1.0F - x));
    }

    static float inOutSine(float x) {
        return -(Mth.cos((float) Math.PI * x) - 1.0F) / 2.0F;
    }

    static float inOutExpo(float x) {
        if (x < 0.5F) {
            return x == 0.0F ? 0.0F : (float) (Math.pow(2.0, 20.0 * x - 10.0) / 2.0);
        }
        return x == 1.0F ? 1.0F : (float) ((2.0 - Math.pow(2.0, -20.0 * x + 10.0)) / 2.0);
    }

    static float outBack(float x) {
        return 1.0F + 2.70158F * cube(x - 1.0F) + 1.70158F * Mth.square(x - 1.0F);
    }

    static float inOutBack(float x) {
        if (x < 0.5F) {
            return 4.0F * x * x * (7.189819F * x - 2.5949094F) / 2.0F;
        }
        float dt = 2.0F * x - 2.0F;
        return (dt * dt * (3.5949094F * dt + 2.5949094F) + 2.0F) / 2.0F;
    }

    static float outCubic(float x) {
        return 1.0F - cube(1.0F - x);
    }

    static float inOutElastic(float x) {
        if (x == 0.0F) {
            return 0.0F;
        } else if (x == 1.0F) {
            return 1.0F;
        }
        double sin = Math.sin((20.0 * x - 11.125) * (float) Math.PI * 4.0F / 9.0F);
        return x < 0.5F ? (float) (-(Math.pow(2.0, 20.0 * x - 10.0) * sin) / 2.0) : (float) (Math.pow(2.0, -20.0 * x + 10.0) * sin / 2.0 + 1.0);
    }

    static float outCirc(float x) {
        return (float) Math.sqrt(1.0F - Mth.square(x - 1.0F));
    }

    static float inCirc(float x) {
        return (float) (-Math.sqrt(1.0F - x * x)) + 1.0F;
    }

    /** 26.x Mth.cube. */
    private static float cube(float x) {
        return x * x * x;
    }
}
