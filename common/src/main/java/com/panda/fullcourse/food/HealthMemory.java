package com.panda.fullcourse.food;

/**
 * A player's health from before the game clamped it to a max health that hadn't got its meal bonus back yet: read
 * from the save at login, or copied across a respawn that keeps everything (the End exit). {@link FoodRules} puts the
 * benefits back first, then this health. Implemented on players by a mixin; 0 means nothing to restore.
 */
public interface HealthMemory {
    float fullcourse$savedHealth();

    void fullcourse$setSavedHealth(float health);
}
