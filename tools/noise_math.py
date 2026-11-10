"""Mojang's NormalNoise amplitude math in both versions, to prove a 26.x noise file equals a 1.21.1 one.

Both versions sum two PerlinNoise stacks (the second at input factor 1.0181268882175227) seeded per octave by the
octave index, at the same frequencies (2^(first octave + i)). What can differ is the factor each octave's raw value
is multiplied by; these functions return that factor per octave for each version (26.x NormalNoise.buildOctaves,
computeNormalizationFactor, computeParityNormalizationFactor; 1.21.1 NormalNoise + PerlinNoise constructors).
"""
import math

SQRT2 = math.sqrt(2.0)


def _parity_deviation(span):
    return 0.1 * (1.0 + 1.0 / (span + 1))


def _span(mods):
    used = [i for i, m in enumerate(mods) if m != 0.0]
    return max(used) - min(used)


def factors_1211(amplitudes):
    """1.21.1: PerlinNoise value factor 2^(n-1)/(2^n-1) * 0.5^i * amplitude_i, times NormalNoise's
    0.16666666666666666 / expectedDeviation(maxOctave - minOctave)."""
    n = len(amplitudes)
    lowest = 2.0 ** (n - 1) / (2.0 ** n - 1.0)
    value_factor = 0.16666666666666666 / _parity_deviation(_span(amplitudes))
    return [a * lowest * 0.5 ** i * value_factor for i, a in enumerate(amplitudes)]


def factors_26x(base_amplitude, count, normalize, mods):
    """26.x: buildOctaves amplitudes times the normalization factor (ENABLED, DISABLED or LEGACY)."""
    amp = base_amplitude
    if normalize != "disabled":
        amp = base_amplitude * (0.5 ** -(count - 1) / (0.5 ** -count - 1.0))
    octaves = []
    for i in range(count):
        m = mods[i] if mods else 1.0
        if m != 0.0:
            octaves.append(amp * m)
        else:
            octaves.append(0.0)
        amp *= 0.5
    target = sum(abs(a) for a in octaves)
    deviation = math.sqrt(sum((0.2702247831245211 * abs(a)) ** 2 for a in octaves))
    factor = 0.0 if deviation == 0.0 else (target * 0.3333333333333333) / (deviation * SQRT2)
    if normalize == "legacy" and factor != 0.0:
        factor = base_amplitude * 0.5 * 0.3333333333333333 / _parity_deviation(_span(mods or [1.0] * count))
    return [a * factor for a in octaves]

