package com.nexo.client.mixin;

import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void nexo$repairPageOnOpen(CallbackInfo ci) {
        var pages = (FabricCreativeModeInventoryScreen) this;
        // Fabric retains a static page across screen instances. Common tabs such
        // as Search and Operator Utilities inherit that page even on a new server.
        if (pages.getCurrentPage() >= pages.getPageCount()
                || !pages.getSelectedTab().shouldDisplay()) {
            pages.switchToPage(0);
            pages.setSelectedTab(CreativeModeTabs.getDefaultTab());
        }
    }
}
