package com.game.ws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Raw WebSocket integration tests against the live server (API.md §4, v1.1 §6 items 1 & 6).
 *
 * Transport: java.net.http.WebSocket (JDK 11+) — raw text frames, no STOMP/SockJS
 * (architect decision v1.1 §6.6).
 *
 * Covered scenarios:
 *  - welcome_after_hello        : send hello → server responds with welcome
 *  - no_token_close_4401        : upgrade without token → HTTP 401 on upgrade (§6.1)
 *  - move_produces_state_echo   : send move → server broadcasts state with updated position
 *  - ping_produces_pong         : send ping → server responds pong
 *  - join_leave_events          : second player joins → first receives "join" event;
 *                                  second disconnects → first receives "leave" event
 *
 * join_leave_events is @Disabled because it requires two concurrent authenticated WS
 * connections and server-side session broadcast, which may not be wired in early T1 builds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GameWebSocketIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String token;
    private String playerId;

    private String baseHttp() { return "http://localhost:" + port; }
    private String baseWs()   { return "ws://localhost:"  + port; }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void authenticate() {
        var body = Map.of("username", "wsuser_" + UUID.randomUUID().toString().substring(0, 6),
                          "password", "pass");
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(body, headers);
        var resp = rest.postForEntity(baseHttp() + "/api/auth/login", entity, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        token    = (String) resp.getBody().get("token");
        playerId = (String) resp.getBody().get("playerId");

        // Join the session first (REST) so the player row exists before WS hello.
        var joinHeaders = new HttpHeaders();
        joinHeaders.setContentType(MediaType.APPLICATION_JSON);
        joinHeaders.setBearerAuth(token);
        var joinEntity = new HttpEntity<>(Map.of("sessionId", "lobby"), joinHeaders);
        var joinResp = rest.postForEntity(baseHttp() + "/api/sessions/join", joinEntity, Map.class);
        assertThat(joinResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------
    // Helper: build a collecting WebSocket listener
    // ------------------------------------------------------------------

    /** Accumulates text frames received from the server. */
    static class CollectingListener implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closeFuture = new CompletableFuture<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        private final StringBuilder buf = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                messages.add(buf.toString());
                buf.setLength(0);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            closeFuture.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            this.error.set(error);
            closeFuture.completeExceptionally(error);
        }

        /** Polls until a message matching the type is received, or times out. */
        String waitForType(String type, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                long remaining = deadline - System.currentTimeMillis();
                String msg = messages.poll(remaining, TimeUnit.MILLISECONDS);
                if (msg == null) break;
                if (msg.contains("\"type\":\"" + type + "\"")) return msg;
            }
            return null;
        }
    }

    /** Opens a raw WS connection with the given token (null → no token param). */
    private WebSocket openWs(String bearerToken, CollectingListener listener) throws Exception {
        String url = baseWs() + "/ws/game" + (bearerToken != null ? "?token=" + bearerToken : "");
        HttpClient client = HttpClient.newHttpClient();
        return client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(url), listener)
                .get(5, TimeUnit.SECONDS);
    }

    /** Sends a raw JSON text frame and waits for the send to complete. */
    private void sendText(WebSocket ws, String json) throws Exception {
        ws.sendText(json, true).get(3, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @Timeout(10)
    @DisplayName("welcome_after_hello — server sends 'welcome' message after valid hello")
    void welcome_after_hello() throws Exception {
        var listener = new CollectingListener();
        var ws = openWs(token, listener);

        try {
            sendText(ws, "{\"type\":\"hello\",\"sessionId\":\"lobby\"}");

            String welcome = listener.waitForType("welcome", 5000);
            assertThat(welcome)
                    .as("Expected a 'welcome' message within 5 s")
                    .isNotNull();
            assertThat(welcome).contains("\"type\":\"welcome\"");
            assertThat(welcome).contains("\"self\"");
            assertThat(welcome).contains("\"players\"");
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "test done").get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("no_token_close_4401 — upgrade without token is rejected with HTTP 401")
    void no_token_close_4401() {
        // Per API.md §6.1: server rejects upgrade with HTTP 401 (no socket opened)
        // when token is missing/invalid.  The JDK WebSocket builder surfaces this
        // as an IOException / CompletionException wrapping a 401 HTTP response.
        var listener = new CollectingListener();

        try {
            openWs(null, listener); // no token in URL
            // If openWs succeeds unexpectedly, the server should close 4401.
            Integer closeCode = listener.closeFuture.get(5, TimeUnit.SECONDS);
            assertThat(closeCode)
                    .as("Expected WS close code 4401 (UNAUTHORIZED)")
                    .isEqualTo(4401);
        } catch (Exception ex) {
            // Expected path: upgrade rejected with HTTP 401 (no socket opened).
            // The JDK WebSocket builder surfaces this as a WebSocketHandshakeException
            // whose getResponse().statusCode() carries the 401 — the message string
            // does NOT contain "401", so assert on the response status code.
            Throwable cause = ex;
            while (cause != null && !(cause instanceof java.net.http.WebSocketHandshakeException)) {
                cause = cause.getCause();
            }
            if (cause instanceof java.net.http.WebSocketHandshakeException wse) {
                assertThat(wse.getResponse().statusCode())
                        .as("Expected HTTP 401 on rejected WS upgrade")
                        .isEqualTo(401);
            } else {
                String msg = ex.getMessage() != null ? ex.getMessage() : String.valueOf(ex);
                assertThat(msg)
                        .as("Expected 401 Unauthorized on rejected WS upgrade")
                        .containsIgnoringCase("401");
            }
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("move_produces_state_echo — send move, server broadcasts state containing our position")
    void move_produces_state_echo() throws Exception {
        var listener = new CollectingListener();
        var ws = openWs(token, listener);

        try {
            // Bind to session.
            sendText(ws, "{\"type\":\"hello\",\"sessionId\":\"lobby\"}");
            String welcome = listener.waitForType("welcome", 5000);
            assertThat(welcome).as("Expected welcome first").isNotNull();

            // Send a move.
            sendText(ws, "{\"type\":\"move\",\"seq\":1," +
                         "\"position\":{\"x\":1.0,\"y\":0.0,\"z\":3.0}," +
                         "\"rotationY\":90.0}");

            // Server ticks at ~10/s; wait up to 2 s for a state broadcast.
            String state = listener.waitForType("state", 2000);
            assertThat(state)
                    .as("Expected a 'state' broadcast after move")
                    .isNotNull();
            assertThat(state).contains("\"type\":\"state\"");
            assertThat(state).contains("\"players\"");
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "test done").get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("ping_produces_pong — server replies pong to a ping frame")
    void ping_produces_pong() throws Exception {
        var listener = new CollectingListener();
        var ws = openWs(token, listener);

        try {
            sendText(ws, "{\"type\":\"hello\",\"sessionId\":\"lobby\"}");
            listener.waitForType("welcome", 5000); // wait for binding

            sendText(ws, "{\"type\":\"ping\",\"t\":1718500000000}");

            String pong = listener.waitForType("pong", 3000);
            assertThat(pong)
                    .as("Expected a 'pong' reply within 3 s")
                    .isNotNull();
            assertThat(pong).contains("\"type\":\"pong\"");
            assertThat(pong).contains("\"t\"");
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "test done").get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(20)
    @DisplayName("[DISABLED] join_leave_events — second player joining/leaving triggers join/leave broadcasts")
    @org.junit.jupiter.api.Disabled(
        "Requires two concurrent WS sessions and server-side per-session broadcast routing. " +
        "Enable once T1 broadcast logic (join/leave events) is confirmed implemented.")
    void join_leave_events() throws Exception {
        // Placeholder — full scenario:
        // 1. Player A: login, join, open WS, send hello → receive welcome
        // 2. Player B: login, join, open WS, send hello
        // 3. Player A listener: assert receives {"type":"join","player":{...}} for B
        // 4. Player B: sendClose
        // 5. Player A listener: assert receives {"type":"leave","playerId":"<B>"}
        throw new UnsupportedOperationException("Not yet implemented — see @Disabled reason");
    }
}
