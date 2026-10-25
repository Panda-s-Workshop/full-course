# Full Course

[![Build](https://github.com/Panda-s-Workshop/full-course/actions/workflows/build.yml/badge.svg)](https://github.com/Panda-s-Workshop/full-course/actions/workflows/build.yml)

**Website:** https://panda-s-workshop.github.io/full-course/ (every dish, recipe and number)

**Download:** not released yet (CurseForge link here after the first release).

A Minecraft 26.3 mod for NeoForge and Fabric, Java 25.

- **NeoForge:** 26.3.0.89 or later. Nothing else is needed.
- **Fabric:** Fabric Loader 0.19.5 or later and Fabric API 0.161.0+26.3 or later. Nothing else is needed.

It takes the place of the vanilla food bar, so it doesn't belong in a pack with another mod that reworks hunger.

**No more hunger.** The food bar is gone, sprinting always works and health comes back on its own a few seconds after
the last hit. Food becomes a choice that makes you stronger, and the more work a dish took (steps, stations,
ingredients), the stronger and longer its benefits. Every food from every mod gets a tier from its recipe chain
(see Compatibility for recipes it can't read).

## Playing

- **Meals.** Food heals over time, and a cooked dish also fills a meal slot in the row where the food bar was. Its
  benefits (more hearts, damage, armor, speed, mining speed and more) last while its ring runs down. You start with
  two slots; a new dish over a full row replaces the meal with the least time left.
- **Stars.** A food's tier, one to five stars on its tooltip, comes from the work in its recipe chain: steps,
  stations and ingredients. More stars give more benefits, higher levels and longer meals. Hold Shift on a food to
  see its chain.
- **The kitchen.** The Cutting Board (food on it, then a knife), the Cooking Pot (heat under it, such as a lit
  campfire), the Brick Oven (bakes with fuel) and the Fermenting Crock (seal it and wait) make the better dishes.
  Feasts are placed and shared: one helping per player every five minutes.
- **More slots.** Taste different dishes to open up to three more slots. The Tasting Journal lists what you have
  tasted and what the next slot asks for.
- **Health.** Health comes back on its own 8 seconds after the last hit (Recovery). Mending meals make it faster.

## Commands

- `/fullcourse tier [item]`: a food's tier, score and benefits, for the item named or the one in your hand.
- `/fullcourse meals [player]`: the meals you hold, your meal slots, how many dishes you have tasted and the next slot.
  Naming another player needs operator rights.
- `/fullcourse unlock <players> <slots>` (operators): raises the players' meal slots to that many, up to the config's
  maximum. It never takes slots away.
- `/fullcourse dump` (operators): writes every food's tier, score, recipe chain and families to
  `fullcourse-profiles.csv` in the world folder, one line per food, to check a large pack's ratings in a spreadsheet.

## Configuration

The settings live on the server and are sent to each player when they join, so tooltips, the meal row and the meal
slots always match the server.

- NeoForge: `config/fullcourse-synced.toml`, created with the defaults and a comment for each key on first start.
- Fabric: `config/fullcourse.properties`, created the same way. It is read once at startup.

Restart the server (or the game, in single player) after editing either file. A value that can't be used is replaced by
the nearest allowed one or the default, and the log names the key. NeoForge's in-game config screen marks
these keys the same way: they take effect on the next world load.

The `natural_health_regeneration` gamerule works as in vanilla: turned off (as UHC packs do), nothing heals on its own,
neither Recovery nor food. Healing potions, golden apples and other effects still work.

| Key | Default | What it does |
|---|---|---|
| `keepVanillaHunger` | `false` | Keep the vanilla food bar and regeneration. Meals still give their benefits, except Mending: with Recovery off it has nothing to speed up (tooltips say so). |
| `recoveryDelaySeconds` | `8` | Seconds without taking damage before health starts coming back (0–600). |
| `recoveryIntervalTicks` | `80` | Ticks between heals of 1 HP, before meal benefits shorten it (0–12000; 0 turns Recovery off, and food still heals). |
| `startingMealSlots` | `2` | Meal slots a new player has (1–16). |
| `maxMealSlots` | `5` | The most meal slots a player can have (1–16). The Tasting Journal opens three past the starting slots; `/fullcourse unlock` can go up to this. |
| `keepMealsOnDeath` | `false` | Keep held meals through death. |
| `durationMultiplier` | `1.0` | Multiplies every meal's duration (0.05–20). |
| `tierThresholds` | `1,3,5,8,12` | Lowest recipe-chain score of tiers 1 to 5: exactly five, each higher than the last, 1–999. Anything else falls back to the defaults. |
| `slotMilestones` | `8,15,20` | Different dishes to taste for meal slot 3 (two stars or more), 4 and 5 (three stars or more; slot 5 also needs a feast). Exactly three, each 1–9999, and slot 5's not below slot 4's; anything else falls back to the defaults. Raise them in a pack with many food mods. |

Each player also has three display settings of their own, which the server never sees: `config/fullcourse-client.toml`
on NeoForge, `config/fullcourse-client.properties` on Fabric. Restart the game after editing.

| Key | Default | What it does |
|---|---|---|
| `mealRowOffset` | `0` | GUI pixels to lift the meal row by, to make room for another mod's HUD row (0–200). |
| `showRecoveryParticles` | `false` | Show a heart each time Recovery heals you. |
| `showTierPips` | `true` | A dot in the tier's colour on the icon of every food that gives a meal, in any slot. |

## Compatibility

Full Course rates every food in the game from its recipes, including foods from other mods, so it needs no list of
supported mods. Machine recipes that don't list their ingredients the usual way (Create's, for instance) are read from
their own fields where they can be. Recipes it still can't read are counted in one line of the log at server start,
by recipe type; a food made only that way rates as a raw ingredient until a pack rates it (see below). Some mods get
more than that:

- **Farmer's Delight Refabricated** (Fabric): its dishes get tiers from their cooking-pot and cutting-board recipes
  (a cut such as minced beef or bacon is still a raw ingredient, but the cut counts toward every dish made from it),
  its knives work on the Cutting Board, and Full Course's ingredients carry the shared `c:` tags.
- **JEI**: a recipe category for each kitchen station. Hover the Cooking Pot's arrow for what the grid makes, or
  click it for all pot recipes.
- **AppleSkin**: its hunger overlays and tooltips are hidden, since there is no food bar to show them on. With
  `keepVanillaHunger` on they stay.
- **Cooking for Blockheads**: the Cooking Table lists Full Course's crafted dishes and ingredients and can sort them
  by tier, and its oven heats a Cooking Pot set on top while the oven is cooking something.

## For pack makers

Everything below is data, so a data pack changes it; `/reload` re-rates every food.

**Rating a food by hand.** `data/<namespace>/fullcourse/food_profiles/<item path>.json` rates the item
`<namespace>:<item path>`. Every field is optional; what's left out comes from the recipes. An empty object `{}`
puts a replaced file back to the scored values.

```json
{"tier": 4, "families": ["brawn", "vigor"], "remedy": false, "serving_of": "minecraft:cake"}
```

- `tier`: stars, 0 to 5 (0 gives no meal).
- `families`: any of `vigor`, `brawn`, `fortitude`, `swift`, `diligence`, `fortune`, `tide`, `night_eye`, `ember`,
  `lightfoot`, `mending`. A name not in this list is logged and skipped; an empty list leaves the families to the
  recipes.
- `remedy`: true for foods like the golden apple, which keep their own effects and heal but give no meal.
- `serving_of`: the dish this is a serving or slice of (a feast block, a pie); it rates exactly as that dish.

Full Course's own dishes are rated by files at these paths too (`data/fullcourse/fullcourse/food_profiles/<dish>.json`,
and cake's in `data/minecraft/`), which set their families and leave the tier to the recipes. A pack file at one of
those paths replaces the whole file: a file with only `tier` leaves the dish's families to its recipes, so copy the
families to keep them. A food in `fullcourse:soups` gets Mending only when its families come from its recipes;
families set in a file are used as written.

**What the scorer can't see.** Fluids a machine pours in (Create's filling, for instance) aren't ingredients; only
items are. Another mod's tool that a crafting recipe uses and gives back (a rolling pin, a knife) counts as an
ingredient unless it is in `fullcourse:containers`, and a non-edible intermediate (a flour, a paste) ends the chain as
a raw ingredient unless it is in `fullcourse:culinary`: tag them there. The recipe lines a tooltip shows with Shift
are Full Course's own recipes as shipped; a data pack's changed recipes rate correctly but don't change those lines.

**Item tags** (namespace `fullcourse`):

| Tag | What it means |
|---|---|
| `raw_ingredients` | Obtained raw (crops, drops, fruit): always the start of a recipe chain, never a meal. |
| `culinary` | Not edible, but followed down its recipes as an ingredient (flour, sugar). Anything edible already is. |
| `containers` | Bowls, bottles, buckets: ignored as ingredients. |
| `remedies` | Their own effects and the heal only, no meal. |
| `placed_meals` | Placed and eaten as a block (feasts, cake): their score gets +3. A dish named only by a `serving_of` override (another mod's feast block) gets +1. |
| `soups` | Always give Mending. |
| `groups/<family>` | The ingredients that give each benefit family to foods rated from their recipes. Full Course's own dishes, Grilled Fillet, Flatbread and cake have their families set in their food profiles, so these tags don't change them. |
| `journal_foods` | The "any simple dish" the Tasting Journal's recipe takes. |
| `repairs_flint_knife` | What mends a Flint Knife on an anvil (flint). |

**Block tags:** `fullcourse:heat_sources` heat a Cooking Pot set on top; it holds vanilla's `#minecraft:campfires` and
`#minecraft:fire`, so other mods' campfires count. A block with a `lit` or `burning` state heats
only while that state is on; any other block in the tag always heats. `fullcourse:heat_conductors` (a hopper, so a pot
can be emptied from below) pass heat up from a source under them.

**Effect tag:** `fullcourse:needs_hunger` lists effects that act only through the food bar (Farmer's Delight's
Nourishment and Comfort). With hunger off they do nothing, and tooltips grey them out and say so. Vanilla Hunger isn't
in it: here it is Queasy, which stops new meals from starting and pauses Recovery.

**Biome tags:** `fullcourse:has_wild_onions` and `fullcourse:has_wild_ember_peppers` choose where the wild crops grow.

**Shared tags.** Recipes take `#c:eggs`, `#c:foods/dough`, `#c:foods/cooked_egg`, `#c:foods/raw_meat`,
`#c:foods/raw_fish` and `#c:crops/onion`, so other mods' items work in them. The mod puts its onion, ember pepper,
dough, fried egg, grilled fillet and flatbread in the matching `c:` tags.

**Recipe types**, for data packs:

| Type | Fields |
|---|---|
| `fullcourse:cutting` | `ingredient`, `result` (Cutting Board, one knife use) |
| `fullcourse:cooking` | `ingredients` (up to 6), optional `container` the dish is served in, `result`, optional `time` (ticks, 1 to 32767, default 200), optional `soup_color` (`"#RRGGBB"`) |
| `fullcourse:baking` | `ingredients` (up to 4), `result`, optional `time` (1 to 32767, default 300) |
| `fullcourse:fermenting` | `ingredients` (up to 4), `result`, optional `time` (1 to 1000000, default 12000; sleeping counts) |

Every other mod's recipe type counts too: each type is its own station in a dish's score.

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
