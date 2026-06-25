package com.game.sim;

import com.game.core.weather.WeatherModel;
import com.game.sim.cellar.Fermenter;
import com.game.sim.vine.VineSimulator;

/**
 * Test-only service locator that instantiates the concrete sim implementations
 * for headless acceptance tests.
 *
 * <p>The sim module is intentionally NOT a Spring context — the determinism and
 * acceptance tests run as plain JUnit 5 without {@code @SpringBootTest} so they
 * are fast and have no infrastructure dependencies.  This locator is the single
 * place that knows about concrete class names; all test classes reference only
 * the frozen §3 interfaces.
 *
 * <h2>How to update when L1/L2/L3 deliver their implementations</h2>
 * Replace each {@code TODO} factory method body with the appropriate
 * {@code new ConcreteClass()} call.  Constructor signatures must follow the
 * SIM-SPEC §3 and Spring Boot idioms (constructor injection internally, but
 * no-arg or seed-agnostic construction here since the seed is passed per-run).
 *
 * <h2>Why not use ServiceLoader?</h2>
 * ServiceLoader adds deployment complexity.  Since this is a test-only helper
 * and the concrete classes will be in the same Gradle source set, direct
 * instantiation is simpler and faster.
 */
final class SimServiceLocator {

    private SimServiceLocator() {}

    /**
     * Returns the concrete {@link WeatherModel} implementation from L1
     * ({@code com.game.core.weather}).
     *
     * <p>Spec ref: §3.2 — {@code generateYear} and {@code rollVintage} must be
     * deterministic from (seed, region, year).
     */
    static WeatherModel weatherModel() {
        return new com.game.core.weather.KakhetiWeatherModel();
    }

    /**
     * Returns the concrete {@link VineSimulator} implementation from L2
     * ({@code com.game.sim.vine}).
     *
     * <p>Spec ref: §3.4 — {@code tick} must be pure (same inputs → same output).
     */
    static VineSimulator vineSimulator() {
        return new com.game.sim.vine.KakhetiVineSimulator();
    }

    /**
     * Returns the concrete {@link Fermenter} implementation from L3
     * ({@code com.game.sim.cellar}).
     *
     * <p>Spec ref: §3.6 — deterministic fermentation kinetics; RED style
     * cellar temp 21–30°C.
     */
    static Fermenter fermenter() {
        return new com.game.sim.cellar.KineticFermenter();
    }
}
