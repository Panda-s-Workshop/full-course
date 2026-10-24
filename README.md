# Full Course

[![Build](https://github.com/Panda-s-Workshop/full-course/actions/workflows/build.yml/badge.svg)](https://github.com/Panda-s-Workshop/full-course/actions/workflows/build.yml)

**Download:** not released yet (CurseForge link here after the first release).

A Minecraft 26.3 mod for NeoForge (26.3.0.89) and Fabric (Fabric API 0.161.0), Java 25.

**No more hunger.** The food bar is gone, sprinting always works and health comes back on its own a few seconds after
the last hit. Food becomes a choice that makes you stronger, and the more work a dish took (steps, stations,
ingredients), the stronger and longer its benefits. Every food from every mod gets a tier from its recipe chain.

## Building and running

JDK 25 is pinned in `gradle.properties` (`org.gradle.java.home`); CI overrides it.

```bash
./gradlew build
```

```bash
./gradlew :neoforge:runGameTestServer
```

- `build` writes `neoforge/build/libs/fullcourse-<version>-neoforge.jar` and `fabric/build/libs/fullcourse-<version>-fabric.jar`.
- JUnit tests live in `common/src/test`, GameTests in `neoforge/src/main/java/com/panda/fullcourse/gametest`.

## License

All Rights Reserved.
