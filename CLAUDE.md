# Project: QVEVRI — Georgian wine-trade MMO (Unreal client + Java backend)

## Layout
- client-unreal/ -> Unreal Engine 5.7 + Cesium for Unreal (C++) — CURRENT client
- server/        -> Spring Boot (Java 21, Maven)
- docs/          -> shared design + API contract (docs/API.md is the source of truth)

> History: the project began with a Unity (C#) client under `client/`. It was
> migrated to Unreal Engine; `client-unreal/` is the live client. If a `client/`
> Unity tree still exists, treat it as legacy/deprecated — do not build on it.

## Rules for all agents
- Read docs/API.md before changing any client<->server contract; update it in the same change.
- Never edit files outside your ownership lane (see your agent definition).
- Write a one-line summary to docs/PROGRESS.md when you finish a task.
- C++: follow Unreal Engine conventions; Java: Spring Boot idioms, constructor injection.
- No secrets in code. Add tests for new logic.

## Notes
- The assistant cannot run Maven in its sandbox; run `mvn test` on Windows (JDK 21)
  and paste the output.
- Backend lanes are additive: new package, pre-assigned Flyway version, keep all
  tests green, "default/no-bonus character unchanged."
- See docs/SESSION-HANDOFF.md for current project state when resuming work.
