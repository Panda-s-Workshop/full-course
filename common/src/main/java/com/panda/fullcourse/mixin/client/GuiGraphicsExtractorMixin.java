package com.panda.fullcourse.mixin.client;

import com.panda.fullcourse.client.TierPips;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The tier pip, drawn after an item's own decorations so it sits over the icon in every slot. */
@Mixin(GuiGraphicsExtractor.class)
abstract class GuiGraphicsExtractorMixin {
    @Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void fullcourse$tierPip(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        TierPips.draw((GuiGraphicsExtractor) (Object) this, stack, x, y);
    }
}
