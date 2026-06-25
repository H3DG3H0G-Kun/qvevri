# Project: Unity 3D game + Java backend

## Layout
- client/  -> Unity (C#)
- server/  -> Spring Boot (Java 21, Maven)
- docs/    -> shared design + API contract (docs/API.md is the source of truth)

## Rules for all agents
- Read docs/API.md before changing any client<->server contract; update it in the same change.
- Never edit files outside your ownership lane (see your agent definition).
- Write a one-line summary to docs/PROGRESS.md when you finish a task.
- C#: follow Unity conventions; Java: Spring Boot idioms, constructor injection.
- No secrets in code. Add tests for new logic.
