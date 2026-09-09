package net.mattwhyy.onemace;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OneMaceRegressionTest {
    private Class<?> mockBukkit;
    private Object server;
    private OneMace plugin;

    @BeforeEach
    void setUp() throws Exception {
        mockBukkit = loadMockBukkitClass();
        server = mockBukkit.getMethod("mock").invoke(null);
        plugin = (OneMace) findLoadMethod().invoke(null, OneMace.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockBukkit != null) {
            mockBukkit.getMethod("unmock").invoke(null);
        }
    }

    @Test
    void recognizesUnmarkedMaces() {
        assertTrue(plugin.isMace(new ItemStack(Material.MACE)));
    }

    @Test
    void findsMaceInsideBundle() {
        ItemStack bundle = createBundleIfSupported(new ItemStack(Material.MACE));

        assertTrue(plugin.containsMace(bundle));
    }

    @Test
    void recognizesShelfNamesWithoutNewApiConstants() {
        assertTrue(MaceStorageUtil.isShelfName("SHELF"));
        assertTrue(MaceStorageUtil.isShelfName("OAK_SHELF"));
        assertTrue(MaceStorageUtil.isShelfName("SPRUCE_SHELF"));
        assertFalse(MaceStorageUtil.isShelfName("OAK_PLANKS"));
    }

    @Test
    void duplicateCleanupPersistsInsideBundleMetadata() throws Exception {
        Player player = addPlayer();
        player.getInventory().setItem(0, new ItemStack(Material.MACE));
        player.getInventory().setItem(1, createBundleIfSupported(new ItemStack(Material.MACE)));

        MaceStorageUtil.CleanupState state = new MaceStorageUtil.CleanupState();
        MaceStorageUtil.removeExtraMaces(player.getInventory(), state);

        assertTrue(state.keptOne());
        assertEquals(1, state.removed());
        assertTrue(plugin.isMace(player.getInventory().getItem(0)));

        ItemStack bundle = player.getInventory().getItem(1);
        assertFalse(plugin.containsMace(bundle));
        assertTrue(((BundleMeta) bundle.getItemMeta()).getItems().isEmpty());
    }

    @Test
    void fixKeepsCraftingDisabledForOfflineMace() throws Exception {
        UUID offlinePlayer = UUID.randomUUID();
        plugin.getConfig().set("offline_inventory." + offlinePlayer, true);
        plugin.lockMaceCrafting();

        Player admin = addPlayer();
        admin.setOp(true);

        runFix(admin);

        assertTrue(plugin.isMaceCrafted());
        assertTrue(plugin.getConfig().getBoolean("settings.mace-crafted"));
        assertTrue(plugin.getConfig().getBoolean("offline_inventory." + offlinePlayer));
    }

    @Test
    void fixDoesNotReenableCraftingWhenCraftedMaceMayBeUnloaded() throws Exception {
        plugin.lockMaceCrafting();

        Player admin = addPlayer();
        admin.setOp(true);

        runFix(admin);

        assertTrue(plugin.isMaceCrafted());
        assertTrue(plugin.getConfig().getBoolean("settings.mace-crafted"));
    }

    private Class<?> loadMockBukkitClass() throws ClassNotFoundException {
        try {
            return Class.forName("org.mockbukkit.mockbukkit.MockBukkit");
        } catch (ClassNotFoundException ignored) {
            return Class.forName("be.seeseemelk.mockbukkit.MockBukkit");
        }
    }

    private Method findLoadMethod() throws NoSuchMethodException {
        for (Method method : mockBukkit.getMethods()) {
            if (method.getName().equals("load")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == Class.class) {
                return method;
            }
        }
        throw new NoSuchMethodException("MockBukkit.load(Class)");
    }

    private Player addPlayer() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return (Player) server.getClass().getMethod("addPlayer").invoke(server);
    }

    private void runFix(Player admin) throws Exception {
        OneMaceCommand command = new OneMaceCommand(plugin);
        command.onCommand(admin, plugin.getCommand("onemace"), "onemace", new String[]{"fix"});

        Object scheduler = server.getClass().getMethod("getScheduler").invoke(server);
        scheduler.getClass().getMethod("performOneTick").invoke(scheduler);
    }

    private ItemStack createBundleIfSupported(ItemStack... contents) {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        ItemMeta itemMeta = bundle.getItemMeta();
        assumeTrue(itemMeta instanceof BundleMeta, "This server test harness does not implement bundle metadata");

        BundleMeta meta = (BundleMeta) itemMeta;
        meta.setItems(List.of(contents));
        bundle.setItemMeta(meta);
        return bundle;
    }
}
