package com.game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.auth.TokenAuthFilter;
import com.game.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TokenAuthFilter tokenAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(TokenAuthFilter tokenAuthFilter, ObjectMapper objectMapper) {
        this.tokenAuthFilter = tokenAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // Allow H2 console frames
            .headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(unauthorizedEntryPoint())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/vineyard/**").permitAll()
                .requestMatchers("/ws/game").permitAll()  // WS handshake auth handled in interceptor
                .requestMatchers("/h2-console/**").permitAll() // H2 console (dev only)
                // MMO-CORE-SPEC §1: new market/cellar/account/character endpoints use
                // their own inline bearer-token check via AccountTokenService.
                .requestMatchers("/api/cellar/**").permitAll()
                .requestMatchers("/api/market/**").permitAll()
                .requestMatchers("/api/account/**").permitAll()
                .requestMatchers("/api/characters/**").permitAll()
                .requestMatchers("/api/world/**").permitAll()
                .requestMatchers("/api/vineyards/**").permitAll()  // estate; inline bearer check
                .requestMatchers("/api/goods/**").permitAll()      // goods catalog + inventory; inline bearer check
                .requestMatchers("/api/shop/**").permitAll()       // NPC bazaar buy/sell; inline bearer check
                .requestMatchers("/api/profession/**").permitAll() // profession actions; inline bearer check
                .requestMatchers("/api/wine/**").permitAll()       // winemaking/fermentation; inline bearer check
                .requestMatchers("/api/land/**").permitAll()       // estates/parcels; inline bearer check
                .requestMatchers("/api/trade/**").permitAll()      // player-to-player trade; inline bearer check
                .requestMatchers("/api/progression/**").permitAll() // XP/reputation; inline bearer check
                .requestMatchers("/api/quests/**").permitAll()     // quests/NPC; inline bearer check
                .requestMatchers("/api/economy/**").permitAll()    // dynamic pricing/indices; inline bearer check
                .requestMatchers("/api/logistics/**").permitAll()  // shipments/transport; inline bearer check
                .requestMatchers("/api/build/**").permitAll()      // estate buildings; inline bearer check
                .requestMatchers("/api/guild/**").permitAll()      // wine houses/guilds; inline bearer check
                .requestMatchers("/api/auction/**").permitAll()    // auction house; inline bearer check
                .requestMatchers("/api/bank/**").permitAll()       // banking/loans; inline bearer check
                .requestMatchers("/api/mail/**").permitAll()       // in-game mailbox; inline bearer check
                .requestMatchers("/api/ranking/**").permitAll()    // leaderboards; inline bearer check
                .requestMatchers("/api/festival/**").permitAll()   // world events; inline bearer check
                .requestMatchers("/api/contest/**").permitAll()    // wine competitions; inline bearer check
                .requestMatchers("/api/achievement/**").permitAll() // achievements; inline bearer check
                .requestMatchers("/api/chat/**").permitAll()       // chat channels/DMs; inline bearer check
                .requestMatchers("/api/research/**").permitAll()   // tech tree; inline bearer check
                .requestMatchers("/api/tourism/**").permitAll()    // estate tourism income; inline bearer check
                .requestMatchers("/api/labor/**").permitAll()      // hired staff/wages; inline bearer check
                .requestMatchers("/api/travel/**").permitAll()     // player travel; inline bearer check
                .requestMatchers("/api/career/**").permitAll()     // career profiles; inline bearer check
                .requestMatchers("/api/skill/**").permitAll()      // talent tree; inline bearer check
                .requestMatchers("/api/prestige/**").permitAll()   // prestige/titles; inline bearer check
                .requestMatchers("/api/export/**").permitAll()     // foreign markets; inline bearer check
                .requestMatchers("/api/bonus/**").permitAll()      // aggregated bonuses; inline bearer check
                .anyRequest().authenticated()
            )
            .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * BCrypt encoder bean -- shared across all components that need password hashing.
     * Declared here so it is available in the Spring context without any extra config.
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Returns HTTP 401 with our standard error envelope for unauthenticated requests.
     */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            ErrorResponse body = new ErrorResponse("UNAUTHORIZED", "Authentication required");
            response.getWriter().write(objectMapper.writeValueAsString(body));
        };
    }
}
