# SCALE-DESIGN — Toward 5,000 concurrent users

How QVEVRI gets to ~5k CCU without rewrites. The guiding principle: **build the
cheap 80% that scales horizontally now, defer the expensive realtime 20% until
realtime gameplay actually exists, and optimize against measurements — not guesses.**

## 1. Where the load actually is
QVEVRI's core loop is an **asynchronous economy over REST**, not a twitch shooter.
At 5k CCU the dominant traffic is: auth, list/plant/grow/harvest, browse/buy market,
read your vineyards, read the clock. These are request/response against a database.

That profile scales the boring, proven way:

```
            ┌──────────────┐
  clients → │ load balancer│ → [ app instance × N ]  (stateless)
            └──────────────┘            │
                                        ▼
                         ┌─────────────────────────┐
                         │ Postgres (primary)      │  writes
                         │   └─ read replicas ×M    │  reads
                         └─────────────────────────┘
                                        ▲
                                   Redis cache  (sessions, hot reads)
```

## 2. Two architectural decisions already paid off
- **Stateless REST economy.** Plant/grow/harvest/market are request/response over the
  DB. Add app instances behind a load balancer; they share nothing. This is the easy path.
- **Deterministic replay, not per-user ticking.** The server does NOT simulate 5,000
  vineyards every frame. A vineyard's state is a pure function of (seed, region, variety,
  year, day) — recomputed only on read, and **cacheable** by (vineyardId, worldDay).
  This removes the classic MMO "tick everything" cost almost entirely.

## 3. What blocks horizontal scale today (fix these — cheap)
1. **In-memory token maps** (`AccountTokenService`, `TokenService`) — per-process, lost on
   restart. → Make auth **stateless** (signed JWT) or externalize to Redis, so any instance
   serves any request.
2. **H2 dev DB** → **Postgres** with **Flyway migrations** (stop trusting `ddl-auto`),
   HikariCP pooling, and **indexes** on hot foreign keys: `mmo_character.accountId`,
   `cellar_item.characterId`, `market_listing.status`, `mmo_vineyard.ownerCharacterId`.
3. **Polling smell:** clients poll the clock every ~3 s. At 5k CCU that's ~1.7k req/s of
   pure clock-polling. The clock is deterministic, so the client should fetch
   `(absoluteDay, epochMs, realSecondsPerSimDay)` **once** and compute the date locally,
   resyncing rarely. Drops it to ~0. (Same idea: cache vineyard replays; widen vineyard
   poll cadence.)

## 4. What is genuinely hard — and deliberately deferred
The realtime WebSocket movement lobby is a single in-memory process broadcasting to all
players. Real-time co-presence at 5k CCU needs zoning, area-of-interest/interest
management, server sharding/instancing, and entity replication — a large, specialized
effort. **Do not build this until there is real 3D multiplayer gameplay worth scaling.**
Today it's a demo slice. When the time comes, options: a dedicated realtime gateway
(e.g. a Netty/!Spring service) sharded by region/zone, Redis pub/sub or NATS for
cross-instance fan-out, and AoI so a player only receives nearby entities.

## 5. Caching strategy (when measurements justify it)
- **Vineyard replay cache:** key (vineyardId, worldDay) → VineyardView; invalidate on
  harvest/plant or day rollover. Turns repeated reads into O(1).
- **Market read cache:** short TTL (1–2 s) on `GET /api/market`; it's read-heavy and a
  slightly stale list is fine. Buy still re-validates the listing transactionally.
- **Redis** for sessions + these caches; it's the one piece of shared state stateless
  instances need.

## 6. Back-of-envelope (5,000 CCU)
- If an active user makes ~1 economy action / 30 s → ~167 req/s of writes. Trivial for one
  Postgres primary; replicas absorb reads.
- Clock/vineyard reads dominate only if polled; §3.3 makes that near-free.
- Realtime movement is the only high-fan-out path and is scoped out (§4).
- Conclusion: a handful of stateless app instances + one well-indexed Postgres (+ a read
  replica + Redis) comfortably handles the async game at 5k. **Verify with a load test
  before adding anything.**

## 7. Sequenced rollout
1. **Now:** Postgres + Flyway + stateless JWT auth + indexes (keep H2 for dev/test green).
2. **Next:** load-test the REST loop (k6/Gatling) at rising concurrency → find real limits.
3. **Then, only if measured:** Redis sessions + replay/market caches; read replicas.
4. **Later (with realtime gameplay):** sharded realtime gateway + AoI.
5. **Cross-cutting:** observability (metrics/traces), so scaling is data-driven.

## 8. Non-goals for this phase
No Kubernetes/microservice split (the modular monolith scales fine horizontally), no
realtime sharding, no premature caching. Add complexity only when a load test shows a wall.
