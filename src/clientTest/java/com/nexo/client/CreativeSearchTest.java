package com.nexo.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CreativeSearchTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                // Reproduce sync before the creative screen has initialized its indexes.
                var trees = client.getConnection().searchTrees();
                for (String name : List.of("creativeByNameSearch", "creativeByTagSearch")) {
                    var field = SessionSearchTrees.class.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(trees, CompletableFuture.completedFuture(SearchTree.empty()));
                }
                if (!trees.creativeNameSearch().search("stone").isEmpty()) {
                    throw new AssertionError("The initial index should be empty");
                }
                syncAndRefresh(client, "Nci First Search Item");
                assertSearch(client, "nci first search item");
                // Parameters are unchanged: this exercises the forced refresh path too.
                syncAndRefresh(client, "Nci Updated Search Item");
                assertSearch(client, "nci updated search item");
                if (!trees.creativeNameSearch().search("nci first search item").isEmpty()) {
                    throw new AssertionError("Search retained an item from the previous snapshot");
                }
                rebuild().invoke(null, List.of(), false);
                refresh().invoke(null, client);
                if (!trees.creativeNameSearch().search("nci updated search item").isEmpty()) {
                    throw new AssertionError("Removed Nexo item remains searchable");
                }
                assertSearch(client, null);
            });
        } catch (Exception e) {
            throw new AssertionError("Creative search regression failed", e);
        }
    }

    private static void syncAndRefresh(Minecraft client, String name) throws Exception {
        ItemStack stack = new ItemStack(Items.BOOK);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        Class<?> groupClass = Class.forName("com.nexo.client.NexoClient$TabGroupData");
        Constructor<?> constructor = groupClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object group = constructor.newInstance("nexo:search_test", "Search Test", stack.copy(), List.of(stack));
        rebuild().invoke(null, List.of(group), false);
        if (!Boolean.TRUE.equals(refresh().invoke(null, client))) {
            throw new AssertionError("NCI refresh did not succeed");
        }
    }

    private static Method rebuild() throws Exception {
        Method method = NexoClient.class.getDeclaredMethod("rebuildSyncedGroups", List.class, boolean.class);
        method.setAccessible(true);
        return method;
    }

    private static Method refresh() throws Exception {
        Method method = NexoClient.class.getDeclaredMethod("refreshCreativeModeTabs", Minecraft.class);
        method.setAccessible(true);
        return method;
    }

    private static void assertSearch(Minecraft client, String customName) {
        var trees = client.getConnection().searchTrees();
        if (trees.creativeNameSearch().search("stone").stream().noneMatch(stack -> stack.is(Items.STONE))) {
            throw new AssertionError("Vanilla stone is missing from name search");
        }
        if (trees.creativeTagSearch().search("planks").stream().noneMatch(stack -> stack.is(Items.OAK_PLANKS))) {
            throw new AssertionError("Vanilla oak planks are missing from tag search");
        }
        if (customName != null && trees.creativeNameSearch().search(customName).stream()
                .noneMatch(stack -> stack.getHoverName().getString().equalsIgnoreCase(customName))) {
            throw new AssertionError("Nexo item is missing from name search: " + customName);
        }
    }
}
