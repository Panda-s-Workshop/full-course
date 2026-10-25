# Full Course

[![Build](https://github.com/Panda-s-Workshop/full-course/actions/workflows/build.yml/badge.svg)](https://github.com/Panda-s-Workshop/full-course/actions/workflows/build.yml)

**Download:** not released yet (CurseForge link here after the first release).

A Minecraft 26.3 mod for NeoForge (26.3.0.89) and Fabric (Fabric API 0.161.0), Java 25.

**No more hunger.** The food bar is gone, sprinting always works and health comes back on its own a few seconds after
the last hit. Food becomes a choice that makes you stronger, and the more work a dish took (steps, stations,
ingredients), the stronger and longer its benefits. Every food from every mod gets a tier from its recipe chain.

## Commands

- `/fullcourse tier [item]`: a food's tier, score and benefits, for the item named or the one in your hand.
- `/fullcourse meals [player]`: the meals you hold, your meal slots, the dishes you have tasted and the next slot.
  Naming another player needs operator rights.
- `/fullcourse unlock <players> <slots>` (operators): raises the players' meal slots to that many, up to the config's
  maximum. It never takes slots away.

## Configuration

The settings live on the server and are sent to each player when they join, so tooltips, the meal row and the meal
slots always match the server.

- NeoForge: `config/fullcourse-synced.toml`, created with the defaults and a comment for each key on first start.
- Fabric: `config/fullcourse.properties`, created the same way. It is read once at startup.

Restart the server (or the game, in single player) after editing either file.

| Key | Default | What it does |
|---|---|---|
| `keepVanillaHunger` | `false` | Keep the vanilla food bar and regeneration. Meals still give their benefits. |
| `recoveryDelaySeconds` | `8` | Seconds without taking damage before health starts coming back (0–600). |
| `recoveryIntervalTicks` | `80` | Ticks between heals of 1 HP, before meal benefits shorten it (1–12000). |
| `startingMealSlots` | `2` | Meal slots a new player has (1–16). |
| `maxMealSlots` | `5` | The most meal slots the Tasting Journal can unlock (1–16). |
| `keepMealsOnDeath` | `false` | Keep held meals through death. |
| `durationMultiplier` | `1.0` | Multiplies every meal's duration (0.05–20). |
| `tierThresholds` | `1,3,5,8,12` | Lowest recipe-chain score of tiers 1 to 5: exactly five, ascending. Anything else falls back to the defaults. |

Each player also has three display settings of their own, which the server never sees: `config/fullcourse-client.toml`
on NeoForge, `config/fullcourse-client.properties` on Fabric. Restart the game after editing.

| Key | Default | What it does |
|---|---|---|
| `mealRowOffset` | `0` | GUI pixels to lift the meal row by, to make room for another mod's HUD row (0–200). |
| `showRecoveryParticles` | `false` | Show a heart each time Recovery heals you. |
| `showTierPips` | `true` | A dot in the tier's colour on the icon of every food that gives a meal, in any slot. |

## Compatibility

Full Course rates every food in the game from its recipes, including foods from other mods, so it needs no list of
supported mods. Some mods get more than that:

- **Farmer's Delight Refabricated** (Fabric): its dishes get tiers from their cooking-pot and cutting-board recipes,
  its knives work on the Cutting Board, and Full Course's ingredients carry the shared `c:` tags.
- **JEI**: a recipe category for each kitchen station. Hover the Cooking Pot's arrow for what the grid makes, or
  click it for all pot recipes.
- **AppleSkin**: its hunger overlays and tooltips are hidden, since there is no food bar to show them on. With
  `keepVanillaHunger` on they stay.
- **Cooking for Blockheads**: the Cooking Table lists Full Course's crafted dishes and ingredients and can sort them
  by tier, and its oven heats a Cooking Pot set on top while the oven is cooking something.

Packs can add heat sources to the `fullcourse:heat_sources` block tag. A block with a `lit` or `burning` state heats
only while that state is on; any other block in the tag always heats.

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
