package net.mattwhyy.onemace;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OneMaceRegressionTest {
    private ServerMock server;
    private OneMace plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OneMace.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void recognizesUnmarkedMaces() {
        assertTrue(plugin.isMace(new ItemStack(Material.MACE)));
    }

    @Test
    void findsMaceInsideBundle() {
        ItemStack bundle = bundleWith(new ItemStack(Material.MACE));

        assertTrue(plugin.containsMace(bundle));
    }

    @Test
    void recognizesShelfMaterials() {
        assertTrue(MaceStorageUtil.isShelf(Material.OAK_SHELF));
        assertTrue(MaceStorageUtil.isShelf(Material.SPRUCE_SHELF));
        assertFalse(MaceStorageUtil.isShelf(Material.OAK_PLANKS));
    }

    @Test
    void duplicateCleanupPersistsInsideBundleMetadata() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, new ItemStack(Material.MACE));
        player.getInventory().setItem(1, bundleWith(new ItemStack(Material.MACE)));

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
    void fixKeepsCraftingDisabledForOfflineMace() {
        UUID offlinePlayer = UUID.randomUUID();
        plugin.getConfig().set("offline_inventory." + offlinePlayer, true);
        plugin.lockMaceCrafting();

        PlayerMock admin = server.addPlayer();
        admin.setOp(true);

        OneMaceCommand command = new OneMaceCommand(plugin);
        command.onCommand(admin, plugin.getCommand("onemace"), "onemace", new String[]{"fix"});
        server.getScheduler().performOneTick();

        assertTrue(plugin.getConfig().getBoolean("settings.mace-crafted"));
        assertTrue(plugin.getConfig().getBoolean("offline_inventory." + offlinePlayer));
    }

    private ItemStack bundleWith(ItemStack... contents) {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        meta.setItems(List.of(contents));
        bundle.setItemMeta(meta);
        return bundle;
    }
}
