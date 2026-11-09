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
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;

public final class CreativeSearchTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                assertPacketDecoding(client);
                assertVersionedChunks(client);
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
                var tab = CreativeModeTabs.allTabs().stream()
                        .filter(candidate -> candidate.getDisplayName().getString().equals("Search Test"))
                        .findFirst().orElseThrow();
                if (!tab.getIconItem().is(Items.BOOK)) {
                    throw new AssertionError("Initial icon should be a book");
                }
                client.gameMode.setLocalMode(net.minecraft.world.level.GameType.CREATIVE);
                client.gui.setScreen(new CreativeModeInventoryScreen(client.player,
                        client.getConnection().enabledFeatures(), true));
                var pages = (FabricCreativeModeInventoryScreen) client.gui.screen();
                pages.setSelectedTab(tab);
                assertGrid(client);
                // Parameters are unchanged: this exercises the forced refresh path too.
                syncAndRefresh(client, "Nci Updated Search Item");
                assertSearch(client, "nci updated search item");
                assertGrid(client);
                if (!tab.getIconItem().is(Items.DIAMOND)) {
                    throw new AssertionError("Tab retained a cached icon from the previous snapshot");
                }
                if (!trees.creativeNameSearch().search("nci first search item").isEmpty()) {
                    throw new AssertionError("Search retained an item from the previous snapshot");
                }
                // A new play handler after a reconnect or proxy reconfiguration must
                // clear the previous server's categories and repair the open page.
                Method begin = NexoClient.class.getDeclaredMethod("beginSession", ClientPacketListener.class);
                begin.setAccessible(true);
                begin.invoke(null, client.getConnection());
                refresh().invoke(null, client);
                if (pages.getSelectedTab() != CreativeModeTabs.getDefaultTab() || pages.getCurrentPage() != 0) {
                    throw new AssertionError("Session reset left a hidden selected tab or stale page");
                }
                if (!trees.creativeNameSearch().search("nci updated search item").isEmpty()) {
                    throw new AssertionError("Removed Nexo item remains searchable");
                }
                assertSearch(client, null);
                syncAndRefresh(client, "Nci New Server Item");
                assertSearch(client, "nci new server item");
                for (int index = 0; index < 72; index++) {
                    Class<?> groupClass = Class.forName("com.nexo.client.NexoClient$TabGroupData");
                    var constructor = groupClass.getDeclaredConstructors()[0];
                    constructor.setAccessible(true);
                    ItemStack item = new ItemStack(Items.DIAMOND);
                    Object group = constructor.newInstance("nexo:category_" + index, "Category " + index, item.copy(), List.of(item));
                    rebuild().invoke(null, List.of(group), true);
                }
                refresh().invoke(null, client);
                for (var category : CreativeModeTabs.tabs()) {
                    if (category.getDisplayName().getString().startsWith("Category ")) {
                        pages.setSelectedTab(category);
                        if (pages.getSelectedTab() != category) {
                            throw new AssertionError("Cannot select category " + category.getDisplayName().getString());
                        }
                        assertGrid(client);
                    }
                }
                // Common tabs keep Fabric's current page rather than owning a page.
                // Closing on search/OP after visiting a late Nexo page must not leave
                // the next server's newly opened inventory on "page 19 of 2".
                pages.setSelectedTab(CreativeModeTabs.searchTab());
                if (pages.getCurrentPage() == 0) {
                    throw new AssertionError("Regression setup did not retain a late page");
                }
                client.gui.setScreen(null);
                begin.invoke(null, client.getConnection());
                refresh().invoke(null, client);
                client.gui.setScreen(new CreativeModeInventoryScreen(client.player,
                        client.getConnection().enabledFeatures(), true));
                var reopened = (FabricCreativeModeInventoryScreen) client.gui.screen();
                if (reopened.getCurrentPage() >= reopened.getPageCount()) {
                    throw new AssertionError("Reopened inventory retained an out-of-range page after server switch");
                }
                if (reopened.getTabsOnPage(reopened.getCurrentPage()).stream()
                        .noneMatch(candidate -> candidate == CreativeModeTabs.getDefaultTab())) {
                    throw new AssertionError("Vanilla category tabs are hidden after reopening");
                }
                client.gui.setScreen(null);
            });
        } catch (Exception e) {
            throw new AssertionError("Creative search regression failed", e);
        }
        try (var nextWorld = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                assertSearch(client, null);
                if (!client.getConnection().searchTrees().creativeNameSearch().search("nci new server item").isEmpty()) {
                    throw new AssertionError("Reconnecting retained the previous server's items");
                }
            });
        }
    }

    private static void assertVersionedChunks(Minecraft client) throws Exception {
        Class<?> payloadClass = Class.forName("com.nexo.client.NexoClient$RegistryPayload");
        Class<?> groupClass = Class.forName("com.nexo.client.NexoClient$TabGroupData");
        var groupConstructor = groupClass.getDeclaredConstructors()[0];
        groupConstructor.setAccessible(true);
        var payloadConstructor = payloadClass.getDeclaredConstructors()[0];
        payloadConstructor.setAccessible(true);
        var apply = NexoClient.class.getDeclaredMethod("applyRegistryPayload", payloadClass, Minecraft.class);
        apply.setAccessible(true);
        for (int index = 0; index < 3; index++) {
            ItemStack item = new ItemStack(Items.BOOK);
            String name = "Nci Versioned Chunk " + index;
            item.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            // First and third packets are separate chunks of the same category.
            Object group = groupConstructor.newInstance("nexo:versioned_" + (index % 2),
                    "Versioned " + (index % 2), item.copy(), List.of(item));
            Object payload = payloadConstructor.newInstance(1, 0, "", "1.1.1",
                    List.of("registry.envelope.v1"), List.of(group));
            apply.invoke(null, payload, client);
        }
        refresh().invoke(null, client);
        for (int index = 0; index < 3; index++) {
            assertSearch(client, "nci versioned chunk " + index);
        }
        long categories = CreativeModeTabs.tabs().stream()
                .filter(tab -> tab.getDisplayName().getString().startsWith("Versioned ")).count();
        if (categories != 2) {
            throw new AssertionError("Versioned chunks did not retain both categories");
        }
        // A proxy play-session change must discard these accumulated categories.
        var begin = NexoClient.class.getDeclaredMethod("beginSession", ClientPacketListener.class);
        begin.setAccessible(true);
        begin.invoke(null, client.getConnection());
        refresh().invoke(null, client);
        if (!client.getConnection().searchTrees().creativeNameSearch().search("nci versioned chunk").isEmpty()) {
            throw new AssertionError("Versioned chunks survived a server-session reset");
        }
    }

    private static void assertPacketDecoding(Minecraft client) throws Exception {
        Class<?> payloadClass = Class.forName("com.nexo.client.NexoClient$RegistryPayload");
        var decode = payloadClass.getDeclaredMethod("decode", net.minecraft.network.RegistryFriendlyByteBuf.class);
        decode.setAccessible(true);
        var status = payloadClass.getDeclaredMethod("status");
        status.setAccessible(true);
        for (int scenario = 0; scenario < 3; scenario++) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), client.getConnection().registryAccess());
            try {
                buffer.writeVarInt(1); // legacy protocol
                buffer.writeVarInt(0); // OK
                buffer.writeUtf("");
                buffer.writeVarInt(scenario == 2 ? 1 : 0);
                if (scenario == 1) {
                    buffer.writeBytes(new byte[]{1, 2, 3}); // reproduce trailing-byte disconnect
                }
                Object decoded = decode.invoke(null, buffer);
                int expectedStatus = scenario == 0 ? 0 : 1;
                if (!Integer.valueOf(expectedStatus).equals(status.invoke(decoded)) || buffer.isReadable()) {
                    throw new AssertionError("Registry decoder did not safely handle scenario " + scenario);
                }
            } finally {
                buffer.release();
            }
        }
    }

    private static void assertGrid(Minecraft client) {
        var screen = (CreativeModeInventoryScreen) client.gui.screen();
        var selected = ((FabricCreativeModeInventoryScreen) screen).getSelectedTab();
        if (screen.getMenu().items.isEmpty() || screen.getMenu().getSlot(0).getItem().isEmpty()) {
            throw new AssertionError("Visible grid empty for " + selected.getDisplayName().getString());
        }
    }

    private static void syncAndRefresh(Minecraft client, String name) throws Exception {
        ItemStack stack = new ItemStack(Items.BOOK);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        Class<?> groupClass = Class.forName("com.nexo.client.NexoClient$TabGroupData");
        Constructor<?> constructor = groupClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ItemStack icon = new ItemStack(name.contains("Updated") ? Items.DIAMOND : Items.BOOK);
        Object group = constructor.newInstance("nexo:search_test", "Search Test", icon, List.of(stack));
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
