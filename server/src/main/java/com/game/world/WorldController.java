package com.game.world;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only world catalog endpoints — no auth required.
 * <ul>
 *   <li>GET /api/world/regions  → RegionInfo[]</li>
 *   <li>GET /api/world/careers  → CareerInfo[]</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/world")
public class WorldController {

    @GetMapping("/regions")
    public ResponseEntity<List<RegionInfo>> regions() {
        return ResponseEntity.ok(WorldCatalog.REGIONS);
    }

    @GetMapping("/careers")
    public ResponseEntity<List<CareerInfo>> careers() {
        return ResponseEntity.ok(WorldCatalog.CAREERS);
    }
}
