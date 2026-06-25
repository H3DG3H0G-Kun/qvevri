# QVEVRI — moving to a new PC

The project is a single git repo containing the Java backend (`server/`), the
Unreal client (`client-unreal/`), and design docs (`docs/`). Git carries the
code; you install the toolchain fresh on the new machine.

Claude has already made the initial commit of all **source + config + docs**.
What remains can only run on a real machine with Git LFS installed — follow the
steps below.

---

## On the OLD PC — finish the push

1. Install Git LFS (once): download from https://git-lfs.com, then:
   ```
   git lfs install
   ```
2. Add the binary Unreal assets (level, region pins, etc.) under LFS:
   ```
   cd <repo>
   git add client-unreal/Content
   git commit -m "Add Unreal Content via git-lfs"
   ```
3. Create a PRIVATE empty repo on GitHub or GitLab (no README/license).
4. Point this repo at it and push:
   ```
   git remote add origin <your-repo-url>
   git push -u origin main
   ```
5. VERIFY in the web UI that you can see `server/`, `client-unreal/Source`,
   `client-unreal/Content`, and `CLAUDE.md`. Do not wipe the old PC until this
   is confirmed.

---

## On the NEW PC — get running

Install first:
- **JDK 21** + **Maven** (backend)
- **Unreal Engine 5.7** (Epic Games Launcher)
- **Visual Studio 2022** + "Game development with C++" workload
- **Git** + **Git LFS**

Then:
```
git lfs install
git clone <your-repo-url>
```

Bring it up:
1. Open `client-unreal/Qvevri.uproject` → let it rebuild the C++ module → accept
   the Cesium for Unreal install prompt → connect your own Cesium ion token.
2. In `server/`, run `mvn test` to confirm the backend builds on JDK 21.
3. Open the Claude desktop app, sign in, and select the cloned `unity-game`
   folder — `CLAUDE.md` restores your project context automatically.

---

## What does NOT travel
- Connectors / MCPs — reconnect in the app (they hold per-machine auth).
- Globally-installed skills/plugins — re-add from the app.
- The cloud scratchpad — holds nothing; nothing to migrate.
