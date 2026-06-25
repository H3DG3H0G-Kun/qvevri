package com.game.vineyard;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the vineyard simulation endpoint.
 *
 * <p>POST /api/vineyard/simulate — no auth required (permitted in SecurityConfig).
 * Validation failures produce 400 via {@link com.game.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/vineyard")
public class VineyardController {

    private final VineyardService vineyardService;

    public VineyardController(VineyardService vineyardService) {
        this.vineyardService = vineyardService;
    }

    /**
     * Run one deterministic simulated year for a vineyard and return the result.
     *
     * @param request validated request body (all fields optional, defaults from VINEYARD-API §1)
     * @return 200 OK with {@link VineyardYearResult}; 400 on validation error
     */
    @PostMapping("/simulate")
    public ResponseEntity<VineyardYearResult> simulate(@Valid @RequestBody VineyardRequest request) {
        VineyardYearResult result = vineyardService.simulate(request);
        return ResponseEntity.ok(result);
    }
}
