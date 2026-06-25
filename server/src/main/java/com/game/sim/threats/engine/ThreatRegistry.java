package com.game.sim.threats.engine;

import com.game.sim.threats.animal.RoeDeer;
import com.game.sim.threats.animal.Rodents;
import com.game.sim.threats.animal.Starlings;
import com.game.sim.threats.animal.WaspsDrosophila;
import com.game.sim.threats.animal.WildBoar;
import com.game.sim.threats.api.ThreatSource;
import com.game.sim.threats.fungal.BlackRot;
import com.game.sim.threats.fungal.Botrytis;
import com.game.sim.threats.fungal.DownyMildew;
import com.game.sim.threats.fungal.EscaTrunkDisease;
import com.game.sim.threats.fungal.PowderyMildew;
import com.game.sim.threats.pest.GrapevineMoth;
import com.game.sim.threats.pest.Leafhopper;
import com.game.sim.threats.pest.Mealybug;
import com.game.sim.threats.pest.Nematode;
import com.game.sim.threats.pest.Phylloxera;
import com.game.sim.threats.pest.SpiderMite;
import com.game.sim.threats.virus.FanleafVirus;
import com.game.sim.threats.virus.LeafrollVirus;
import com.game.sim.threats.weather.DroughtHeatwave;
import com.game.sim.threats.weather.Fire;
import com.game.sim.threats.weather.Flood;
import com.game.sim.threats.weather.Hail;
import com.game.sim.threats.weather.SpringFrost;

import java.util.List;

/**
 * Static factory returning the complete, deterministically ordered list of all
 * {@link ThreatSource} implementations (SIM-THREATS-SPEC §4 Lane D — FROZEN class list).
 *
 * <p>Order is fixed by spec:
 * <ol>
 *   <li>weather:  SpringFrost, Hail, DroughtHeatwave, Flood, Fire</li>
 *   <li>fungal:   DownyMildew, PowderyMildew, Botrytis, BlackRot, EscaTrunkDisease</li>
 *   <li>virus:    FanleafVirus, LeafrollVirus</li>
 *   <li>pest:     Phylloxera, Nematode, GrapevineMoth, Leafhopper, SpiderMite, Mealybug</li>
 *   <li>animal:   Starlings, WildBoar, RoeDeer, Rodents, WaspsDrosophila</li>
 * </ol>
 *
 * <p>Each call returns fresh instances (ThreatSource implementations are stateless;
 * all cross-day state lives in {@link com.game.sim.threats.api.ThreatMemory}).
 */
public final class ThreatRegistry {

    private ThreatRegistry() {}

    /**
     * @return immutable ordered list of all 22 {@link ThreatSource} instances
     */
    public static List<ThreatSource> all() {
        return List.of(
                // ── Weather (Lane A) ─────────────────────────────────────────
                new SpringFrost(),
                new Hail(),
                new DroughtHeatwave(),
                new Flood(),
                new Fire(),

                // ── Fungal (Lane B) ──────────────────────────────────────────
                new DownyMildew(),
                new PowderyMildew(),
                new Botrytis(),
                new BlackRot(),
                new EscaTrunkDisease(),

                // ── Virus (Lane B) ───────────────────────────────────────────
                new FanleafVirus(),
                new LeafrollVirus(),

                // ── Pest (Lane C) ────────────────────────────────────────────
                new Phylloxera(),
                new Nematode(),
                new GrapevineMoth(),
                new Leafhopper(),
                new SpiderMite(),
                new Mealybug(),

                // ── Animal (Lane C) ──────────────────────────────────────────
                new Starlings(),
                new WildBoar(),
                new RoeDeer(),
                new Rodents(),
                new WaspsDrosophila()
        );
    }
}
