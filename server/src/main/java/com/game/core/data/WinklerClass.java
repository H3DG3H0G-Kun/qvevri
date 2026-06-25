package com.game.core.data;

/**
 * Winkler heat-summation class for the season.
 * Boundaries (°C·days, Apr1–Oct31, base 10°C):
 * I &lt;1390, II 1390–1670, III 1670–1940, IV 1940–2220, V &gt;2220.
 * Kakheti (Alazani Valley) typically lands in class III.
 * Frozen per SIM-SPEC §2.
 */
public enum WinklerClass {
    I, II, III, IV, V
}
