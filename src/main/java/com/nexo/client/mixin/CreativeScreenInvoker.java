package com.nexo.client.mixin;

import java.util.Collection;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenInvoker {
    @Invoker("refreshCurrentTabContents")
    void nexo$refreshContents(Collection<ItemStack> items);
}
