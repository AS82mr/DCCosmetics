package dc.dcessentials.modules.items.tooltip;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemCategory;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.scroll.ScrollRegistry;
import dc.dcessentials.modules.teleportation.KeystoneDetector;
import dc.dcessentials.modules.teleportation.KeystoneLoreTokens;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TooltipPacketListener {

    private final DCCosmetics plugin;
    private final ItemRegistry itemRegistry;
    private final ScrollRegistry scrollRegistry;
    private final TooltipModeService modeService;
    private PacketAdapter packetAdapter;

    public TooltipPacketListener(
            DCCosmetics plugin,
            ItemRegistry itemRegistry,
            ScrollRegistry scrollRegistry,
            TooltipModeService modeService
    ) {
        this.plugin = plugin;
        this.itemRegistry = itemRegistry;
        this.scrollRegistry = scrollRegistry;
        this.modeService = modeService;
    }

    public boolean register() {
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            if (pm == null) return false;

            List<PacketType> types = new ArrayList<>();
            types.add(PacketType.Play.Server.SET_SLOT);
            types.add(PacketType.Play.Server.WINDOW_ITEMS);
            try {
                Field f = PacketType.Play.Server.class.getField("SET_CURSOR_ITEM");
                Object val = f.get(null);
                if (val instanceof PacketType pt && pt.isSupported()) {
                    types.add(pt);
                }
            } catch (Throwable ignored) {}

            this.packetAdapter = new PacketAdapter(plugin, ListenerPriority.HIGH, types) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (event.isCancelled()) return;
                    Player viewer = event.getPlayer();
                    if (viewer == null) return;

                    GameMode gm = viewer.getGameMode();
                    if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
                        return; // Creative safety: never rewrite item packets for creative clients
                    }

                    PacketType type = event.getPacketType();
                    PacketContainer packet = event.getPacket();

                    if (type == PacketType.Play.Server.SET_SLOT) {
                        handleSetSlot(viewer, packet);
                    } else if (type == PacketType.Play.Server.WINDOW_ITEMS) {
                        handleWindowItems(viewer, packet);
                    } else {
                        handleCursorItem(viewer, packet);
                    }
                }
            };

            pm.addPacketListener(this.packetAdapter);
            plugin.getLogger().info("[DCEssentials] Registered ProtocolLib TooltipPacketListener (" + types.size() + " packet types).");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[DCEssentials] TooltipPacketListener unavailable (falling back to /dce inspect): " + t.getMessage());
            return false;
        }
    }

    public void unregister() {
        if (this.packetAdapter != null) {
            try {
                ProtocolLibrary.getProtocolManager().removePacketListener(this.packetAdapter);
            } catch (Throwable ignored) {}
            this.packetAdapter = null;
        }
    }

    private void handleSetSlot(Player viewer, PacketContainer packet) {
        UUID uuid = viewer.getUniqueId();
        StructureModifier<Integer> ints = packet.getIntegers();
        int intCount = ints.size();
        int windowId = intCount > 0 ? ints.readSafely(0) : 0;
        int slot = intCount >= 3 ? ints.readSafely(2) : (intCount >= 2 ? ints.readSafely(1) : 0);

        if (windowId > 0 && !modeService.isCachedOpenTopDceGui(uuid)) {
            int topSize = modeService.getCachedOpenTopSize(uuid);
            if (topSize > 0 && slot >= 0 && slot < topSize) {
                return;
            }
        }

        StructureModifier<ItemStack> items = packet.getItemModifier();
        if (items.size() <= 0) return;
        ItemStack original = items.readSafely(0);
        ItemStack rewritten = rewriteForViewer(viewer, original);
        if (rewritten != null) {
            items.write(0, rewritten);
        }
    }

    private void handleWindowItems(Player viewer, PacketContainer packet) {
        UUID uuid = viewer.getUniqueId();
        StructureModifier<Integer> ints = packet.getIntegers();
        int windowId = ints.size() > 0 ? ints.readSafely(0) : 0;
        boolean skipTop = windowId > 0 && !modeService.isCachedOpenTopDceGui(uuid);
        int topSize = skipTop ? modeService.getCachedOpenTopSize(uuid) : 0;

        StructureModifier<List<ItemStack>> listMod = packet.getItemListModifier();
        if (listMod.size() > 0) {
            List<ItemStack> originalList = listMod.readSafely(0);
            if (originalList != null && !originalList.isEmpty()) {
                List<ItemStack> copy = null;
                for (int i = 0; i < originalList.size(); i++) {
                    if (skipTop && i < topSize) continue;
                    ItemStack orig = originalList.get(i);
                    ItemStack rewritten = rewriteForViewer(viewer, orig);
                    if (rewritten != null) {
                        if (copy == null) {
                            copy = new ArrayList<>(originalList);
                        }
                        copy.set(i, rewritten);
                    }
                }
                if (copy != null) {
                    listMod.write(0, copy);
                }
            }
        }

        // Also check carried item on WINDOW_ITEMS if present
        StructureModifier<ItemStack> itemMod = packet.getItemModifier();
        if (itemMod.size() > 0) {
            ItemStack carried = itemMod.readSafely(0);
            ItemStack rewrittenCarried = rewriteForViewer(viewer, carried);
            if (rewrittenCarried != null) {
                itemMod.write(0, rewrittenCarried);
            }
        }
    }

    private void handleCursorItem(Player viewer, PacketContainer packet) {
        StructureModifier<ItemStack> items = packet.getItemModifier();
        if (items.size() <= 0) return;
        ItemStack original = items.readSafely(0);
        ItemStack rewritten = rewriteForViewer(viewer, original);
        if (rewritten != null) {
            items.write(0, rewritten);
        }
    }

    public ItemStack rewriteForViewer(Player viewer, ItemStack original) {
        if (original == null || original.getType().isAir() || !original.hasItemMeta()) {
            return null;
        }

        TooltipMode mode = modeService.getEffectiveMode(viewer.getUniqueId());
        boolean isKeystone = KeystoneDetector.isKeystone(original);
        boolean needExpanded = mode == TooltipMode.EXPANDED && itemRegistry.isDCEItem(original);

        if (!needExpanded && !isKeystone) {
            return null;
        }

        ItemStack clone = null;
        if (needExpanded) {
            String itemId = itemRegistry.getItemId(original);
            DCEItem dceItem = itemRegistry.getItem(itemId);
            if (dceItem != null
                    && dceItem.getCategory() != ItemCategory.SCROLL
                    && dceItem.getCategory() != ItemCategory.COSMETIC_SCROLL) {
                clone = original.clone();
                TooltipRenderer.renderItem(dceItem, clone, scrollRegistry, TooltipMode.EXPANDED);
            }
        }

        if (isKeystone) {
            if (clone == null) {
                clone = original.clone();
            }
            var teleMgr = plugin.getTeleportationManager();
            KeystoneProfile kProfile = teleMgr != null ? teleMgr.getProfile(viewer.getUniqueId()) : null;
            KeystoneLoreTokens.bakeIntoItem(clone, viewer, kProfile);
        }

        return clone;
    }
}
