package com.game.character;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.game.account.AccountTokenService;
import com.game.account.BearerTokenSupport;
import com.game.dto.ErrorResponse;
import com.game.world.CareerType;
import com.game.world.Region;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Character endpoints (bearer token required; validates via AccountTokenService inline).
 * <ul>
 *   <li>POST /api/characters                  -- 201 Character</li>
 *   <li>GET  /api/characters                  -- Character[] for the authenticated account</li>
 *   <li>GET  /api/characters/{id}             -- Character (404 if not owned)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final CharacterService characterService;
    private final AccountTokenService accountTokenService;

    public CharacterController(CharacterService characterService,
                               AccountTokenService accountTokenService) {
        this.characterService = characterService;
        this.accountTokenService = accountTokenService;
    }

    public record CreateCharacterRequest(String name, CareerType careerType, Region homeRegion) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateCharacterRequest req,
                                    HttpServletRequest httpRequest) {
        Long accountId = resolveAccount(httpRequest);
        if (accountId == null) {
            return unauthorized();
        }
        Character character = characterService.create(
                accountId, req.name(), req.careerType(), req.homeRegion());
        return ResponseEntity.status(HttpStatus.CREATED).body(character);
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest httpRequest) {
        Long accountId = resolveAccount(httpRequest);
        if (accountId == null) {
            return unauthorized();
        }
        List<Character> characters = characterService.forAccount(accountId);
        return ResponseEntity.ok(characters);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long accountId = resolveAccount(httpRequest);
        if (accountId == null) {
            return unauthorized();
        }
        return characterService.getOwned(id, accountId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND",
                                "Character not found or does not belong to your account")));
    }

    // -------------------------------------------------------------------------

    private Long resolveAccount(HttpServletRequest request) {
        return BearerTokenSupport.resolveAccountId(request, accountTokenService).orElse(null);
    }

    private ResponseEntity<ErrorResponse> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", "Valid bearer token required"));
    }
}
