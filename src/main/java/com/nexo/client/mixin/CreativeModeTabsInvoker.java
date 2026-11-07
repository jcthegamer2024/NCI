package com.nexo.client.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsInvoker {
    @Invoker("buildAllTabContents")
    static void nexo$buildAllTabContents(CreativeModeTab.ItemDisplayParameters parameters) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
