package com.game.account;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.game.dto.ErrorResponse;

/**
 * MMO account endpoints (permitAll at Spring Security level; token auth handled inline).
 * <ul>
 *   <li>POST /api/account/register  {email, username, password} -- 201 {accountId, token}</li>
 *   <li>POST /api/account/login     {username, password}        -- 200 {accountId, token}</li>
 * </ul>
 * Errors use the standard envelope: {"error":{"code":"...","message":"..."}}.
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountRepository accountRepository;
    private final AccountTokenService accountTokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AccountController(AccountRepository accountRepository,
                             AccountTokenService accountTokenService,
                             BCryptPasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.accountTokenService = accountTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    // -------------------------------------------------------------------------
    // Request / response DTOs
    // -------------------------------------------------------------------------

    public record RegisterRequest(String email, String username, String password) {}
    public record LoginRequest(String username, String password) {}
    public record AuthResponse(Long accountId, String token) {}

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req.email() == null || req.email().isBlank()
                || req.username() == null || req.username().isBlank()
                || req.password() == null || req.password().isBlank()) {
            return badRequest("MISSING_FIELDS", "email, username and password are required");
        }

        if (accountRepository.existsByEmail(req.email().strip())) {
            return badRequest("DUPLICATE_EMAIL", "Email already registered");
        }
        if (accountRepository.existsByUsername(req.username().strip())) {
            return badRequest("DUPLICATE_USERNAME", "Username already taken");
        }

        String hash = passwordEncoder.encode(req.password());
        Account account = new Account(
                req.email().strip(),
                req.username().strip(),
                hash,
                Instant.now().toEpochMilli());
        account = accountRepository.save(account);

        String token = accountTokenService.mint(account.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(account.getId(), token));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req.username() == null || req.username().isBlank()
                || req.password() == null || req.password().isBlank()) {
            return invalidCredentials();
        }

        return accountRepository.findByUsername(req.username().strip())
                .filter(a -> passwordEncoder.matches(req.password(), a.getPasswordHash()))
                .<ResponseEntity<?>>map(a -> {
                    String token = accountTokenService.mint(a.getId());
                    return ResponseEntity.ok(new AuthResponse(a.getId(), token));
                })
                .orElseGet(this::invalidCredentials);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> badRequest(String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(code, message));
    }

    private ResponseEntity<ErrorResponse> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_CREDENTIALS", "Invalid username or password"));
    }
}
