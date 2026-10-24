package com.panda.fullcourse.mixin.client;

import com.panda.fullcourse.client.MealList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The meals beside the survival inventory, drawn with the screen so their tooltip goes out with its own. */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void fullcourse$mealList(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        MealList.extract(g, (InventoryScreen) (Object) this, mouseX, mouseY);
    }
}
