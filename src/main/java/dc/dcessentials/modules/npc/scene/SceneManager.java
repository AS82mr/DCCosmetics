package dc.dcessentials.modules.npc.scene;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages ModelEngine and MythicMobs Scenes deployment (Townsfolks, Farmers, HoverRides, Blacksmiths, Market).
 * Handles atmospheric workstation anchoring, manipulation tools, ambient personality dialogue, and batch clearance.
 */
public class SceneManager implements Listener {

    private final File scenesFile;
    private final List<String> activeSceneUuids = new ArrayList<>();
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

    public SceneManager() {
        File npcDir = new File(DCCosmetics.getInstance().getDataFolder(), "npc");
        if (!npcDir.exists()) npcDir.mkdirs();
        File newScenes = new File(npcDir, "scenes.yml");
        File oldScenes = new File(DCCosmetics.getInstance().getDataFolder(), "npc_scenes.yml");
        if (!newScenes.exists() && oldScenes.exists()) oldScenes.renameTo(newScenes);
        this.scenesFile = newScenes.exists() ? newScenes : (oldScenes.exists() ? oldScenes : newScenes);

        loadScenes();
    }

    public boolean isMythicAvailable() {
        return Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
    }

    private final Map<UUID, UUID> selectedSceneEntity = new HashMap<>();

    public void giveScenesTool(Player player) {
        if (isMythicAvailable()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm items give " + player.getName() + " scenes_tool 1");
        }
        // Always ensure player receives a tagged Scenes Manipulation Tool item if MM item wasn't added
        org.bukkit.inventory.ItemStack tool = new org.bukkit.inventory.ItemStack( org.bukkit.Material.BLAZE_ROD );
        org.bukkit.inventory.meta.ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DragoncoteColor.colorize("&#F59E0B&l✦ Scenes Manipulation Tool"));
            meta.setLore(Arrays.asList(
                    "§8dcnpc_scenes_tool",
                    "§7• §fRight-Click Scene: §7Rotate orientation +15°",
                    "§7• §fShift + Right-Click Scene: §7Open Scene Tweaker GUI",
                    "§7• §fLeft-Click Scene, then Block: §7Move scene to block",
                    "§7• §fShift + Left-Click Scene: §7Safely remove scene"
            ));
            tool.setItemMeta(meta);
        }
        player.getInventory().addItem(tool);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1.0f, 1.4f);
        player.sendMessage(DragoncoteColor.PREFIX_INFO + "Issued Scenes Manipulation Tool!");
    }

    public boolean isScenesTool(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName() && org.bukkit.ChatColor.stripColor(meta.getDisplayName()).toLowerCase().contains("scenes")) {
            return true;
        }
        if (meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                if (line.contains("dcnpc_scenes_tool")) return true;
            }
        }
        return false;
    }

    public List<String> getAllSceneModelIds() {
        return Arrays.asList(
                // Townsfolks (Vol 3)
                "scene_townsfolk_vol3_beggar",
                "scene_townsfolk_vol3_mug",
                "scene_townsfolk_vol3_readers",
                "scene_townsfolk_vol3_swing",
                "scene_townsfolk_vol3_swing_mount",
                "scene_townsfolk_vol3_writer",
                // Farmers & Guards & Combat
                "scene_farmer",
                "scene_guards",
                "scene_guards2",
                "scenes_combat_1",
                // Lumberjacks
                "scene_lumberjack",
                "scene_lumberjack_saw",
                "scene_lumberjack_interactive",
                // Fishermen (unified scene_fishermen.bbmodel + pose aliases)
                "scene_fishermen",
                "scene_fisherman_standing",
                "scene_fisherman_sitting",
                "scene_fisherman_stick",
                "scene_fisherman_boat",
                // Blacksmiths
                "scene_blacksmith_1",
                "scene_blacksmith_2",
                "scene_blacksmith_3",
                "scene_blacksmith_4",
                "scene_blacksmith_5_interactive",
                // Market
                "scene_market_1",
                "scene_market_2",
                "scene_market_3",
                "scene_market_4",
                "scene_market_5",
                "scene_market_6",
                "scene_market_7",
                "scene_market_8",
                "scene_market_9",
                "scene_market_10",
                "scene_market_11",
                "scene_market_12",
                // Atmospheric creatures (Scenes Environment)
                "scenes_pigeons_ground",
                "scenes_pigeon",
                "scenes_pigeon1",
                "scenes_pigeon2",
                "scenes_pigeon3",
                "scenes_pigeons_fly",
                "scenes_crow",
                "scenes_crows",
                "scenes_scarecrow",
                "scenes_scarecrow_playerhead"
        );
    }

    private String[] resolveModelAndAnimation(String requestedId) {
        if (requestedId == null) return new String[]{null, null};
        String lower = requestedId.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "scene_fisherman_stick":
                return new String[]{"scene_fishermen", "stick"};
            case "scene_fisherman_sitting":
                return new String[]{"scene_fishermen", "sitting"};
            case "scene_fisherman_standing":
                return new String[]{"scene_fishermen", "fishing"};
            case "scene_fisherman_boat":
                return new String[]{"scene_fishermen", "boat"};
            case "scene_fisherman_interactive":
                return new String[]{"scene_fishermen", "interactive_idle"};
            case "scene_market_stand":
                return new String[]{"scene_market_1", null};
            case "scene_market_baker":
                return new String[]{"scene_market_2", null};
            case "scene_market_bookseller":
                return new String[]{"scene_market_3", null};
            case "scene_market_fisherman":
                return new String[]{"scene_market_4", null};
            case "ground_crow":
                return new String[]{"scenes_crow", "idle"};
            case "scene_crow_eat":
                return new String[]{"scenes_crow", "eat"};
            case "flying_crows":
                return new String[]{"scenes_crows", "fly"};
            case "flying_crows_wide":
                return new String[]{"scenes_crows", "fly_large"};
            default:
                return new String[]{requestedId, null};
        }
    }

    private void playInitialAnimation(Object activeModel, String animName) {
        if (activeModel == null || animName == null || animName.isEmpty()) return;
        try {
            Object animHandler = activeModel.getClass().getMethod("getAnimationHandler").invoke(activeModel);
            if (animHandler == null) return;
            for (Method m : animHandler.getClass().getMethods()) {
                if (m.getName().equals("playAnimation") && m.getParameterCount() == 5) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts[0] == String.class && pts[4] == boolean.class) {
                        m.invoke(animHandler, animName, 0.25d, 0.25d, 1.0d, true);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Spawns an animated scene model at the target location, attaching nameplates and ModelEngine geometry.
     */
    public Entity spawnSceneModel(String modelId, Location loc, Player player) {
        return spawnSceneModel(modelId, loc, player, null);
    }

    /**
     * Spawns an animated scene model at the target location, attaching nameplates and ModelEngine geometry.
     */
    public Entity spawnSceneModel(String modelId, Location loc, Player player, String customTag) {
        if (loc == null || loc.getWorld() == null) return null;

        String wName = loc.getWorld().getName();
        boolean isBw = wName.toLowerCase().contains("bw") || wName.toLowerCase().contains("drain") || wName.toLowerCase().contains("void");

        String[] resolved = resolveModelAndAnimation(modelId);
        String resolvedBlueprint = resolved[0];
        String initialAnim = resolved[1];

        // 1. First attempt: MythicMobs spawn (if MythicMob definition actually exists)
        Entity ent = spawnSceneMob(modelId, loc);

        // 2. Second attempt / direct ModelEngine attachment (validate blueprint BEFORE spawning ArmorStand)
        if (ent == null) {
            ArmorStand stand = null;
            try {
                Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
                Method createModel = apiClass.getMethod("createActiveModel", String.class);
                Method getOrCreate = apiClass.getMethod("getOrCreateModeledEntity", Entity.class);

                Object activeModel = createModel.invoke(null, resolvedBlueprint);
                if (activeModel == null) {
                    if (player != null) {
                        player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Scene blueprint §f" + resolvedBlueprint + " §cis not loaded in ModelEngine! No entity spawned.");
                    }
                    return null;
                }

                stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setSmall(false);
                stand.setBasePlate(false);
                stand.setArms(false);
                stand.addScoreboardTag("dcnpc_scene");
                stand.addScoreboardTag("scene_model:" + modelId);

                Object modeledEntity = getOrCreate.invoke(null, stand);
                if (modeledEntity == null) {
                    stand.remove();
                    if (player != null) {
                        player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Failed to create ModeledEntity for §f" + resolvedBlueprint + "§c.");
                    }
                    return null;
                }

                Method setBaseVisible = modeledEntity.getClass().getMethod("setBaseEntityVisible", boolean.class);
                setBaseVisible.invoke(modeledEntity, false);

                for (Method m : modeledEntity.getClass().getMethods()) {
                    if (m.getName().equals("addModel") && m.getParameterCount() == 2) {
                        m.invoke(modeledEntity, activeModel, true);
                        break;
                    }
                }

                playInitialAnimation(activeModel, initialAnim);

                try {
                    Method detectPlayers = modeledEntity.getClass().getMethod("detectPlayers");
                    detectPlayers.invoke(modeledEntity);
                } catch (Throwable ignored) {}

                ent = stand;
            } catch (Throwable t) {
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
                if (player != null) {
                    player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Could not spawn scene §f" + modelId + " §7(blueprint §f" + resolvedBlueprint + "§7): " + t.getMessage());
                }
                return null;
            }
        }

        if (ent != null) {
            String rawTitle = (customTag != null && !customTag.isEmpty()) ? customTag : formatSceneTitle(modelId);
            String styledTitle = isBw ? DragoncoteColor.stripToGrayscale(rawTitle) : DragoncoteColor.colorize(rawTitle);
            ent.setCustomName(styledTitle);
            ent.setCustomNameVisible(true);
            ent.addScoreboardTag("dcnpc_scene");
            ent.addScoreboardTag("scene_model:" + modelId);

            activeSceneUuids.add(ent.getUniqueId().toString());
            saveScenes();

            if (player != null) {
                player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.4f);
                player.sendMessage(DragoncoteColor.PREFIX_SUCCESS + "Spawned animated scene §f" + modelId + " §7as " + styledTitle + " §7in §b" + wName + "§7!");
            }
        }
        return ent;
    }

    public Entity spawnSceneMob(String mobId, Location loc) {
        if (!isMythicAvailable()) {
            return null;
        }
        try {
            Class<?> mythicBukkitCls = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = mythicBukkitCls.getMethod("inst").invoke(null);
            Object mobManager = inst.getClass().getMethod("getMobManager").invoke(inst);
            Object activeMob = mobManager.getClass().getMethod("spawnMob", String.class, Location.class).invoke(mobManager, mobId, loc);
            if (activeMob != null) {
                Object entity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
                if (entity != null) {
                    Entity ent = (Entity) entity.getClass().getMethod("getBukkitEntity").invoke(entity);
                    if (ent != null) {
                        activeSceneUuids.add(ent.getUniqueId().toString());
                        saveScenes();
                        return ent;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Do not run blind console command here; spawnSceneModel handles direct ModelEngine fallback cleanly
        }
        return null;
    }

    private String formatSceneTitle(String modelId) {
        String m = modelId.toLowerCase();
        if (m.contains("beggar")) return "<#F59E0B>✦ Street Beggar <#94A3B8>(Sanctuary)";
        if (m.contains("mug")) return "<#F59E0B>✦ Tavern Patron <#94A3B8>(Sanctuary)";
        if (m.contains("reader")) return "<#38BDF8>✦ Sanctuary Reader <#94A3B8>(Archives)";
        if (m.contains("swing")) return "<#EC4899>✦ Park Swing <#94A3B8>(Sanctuary)";
        if (m.contains("writer")) return "<#A855F7>✦ District Scribe <#94A3B8>(Sanctuary)";
        if (m.contains("farmer")) return "<#84CC16>✦ Sanctuary Agronomist <#94A3B8>(Farms)";
        if (m.contains("hoverbike") || m.contains("hover")) return "<#38BDF8>✦ Quantum Speeder <#94A3B8>(Dock)";
        if (m.contains("fisher")) return "<#06B6D4>✦ Harbor Angler <#94A3B8>(Docks)";
        if (m.contains("blacksmith")) return "<#F97316>✦ Tech Forge Workstation <#94A3B8>(Forge)";
        if (m.contains("market")) return "<#FDE68A>✦ Bazaar Merchant Stall <#94A3B8>(Market)";
        if (m.contains("pigeon")) return "<#CBD5E1>Sanctuary Pigeon";
        if (m.contains("crow")) return "<#64748B>Void Crow";
        return "<#E2F6A8>✦ Scene NPC";
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Entity ent = event.getRightClicked();
        if (ent == null || !isSceneEntity(ent)) return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (now - clickCooldown.getOrDefault(player.getUniqueId(), 0L) < 350L) {
            return;
        }
        clickCooldown.put(player.getUniqueId(), now);

        // Handle Scenes Manipulation Tool right-click
        if (isScenesTool(player.getInventory().getItemInMainHand()) && player.hasPermission("dccosmetics.admin")) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                DCCosmetics.getInstance().getSceneEditorGUI().open(player, ent);
            } else {
                Location loc = ent.getLocation().clone();
                loc.setYaw(loc.getYaw() + 15.0f);
                ent.teleport(loc);
                player.playSound(loc, Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
                player.sendMessage(DragoncoteColor.PREFIX_INFO + "Rotated scene entity to §e" + String.format("%.1f°", loc.getYaw()));
            }
            return;
        }

        triggerSceneDialogue(player, ent);
    }

    @EventHandler
    public void onEntityDamageByPlayer(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Entity ent = event.getEntity();
        if (!isSceneEntity(ent)) return;

        Player player = (Player) event.getDamager();
        if (isScenesTool(player.getInventory().getItemInMainHand()) && player.hasPermission("dccosmetics.admin")) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                activeSceneUuids.remove(ent.getUniqueId().toString());
                ent.remove();
                saveScenes();
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.9f, 1.2f);
                player.sendMessage(DragoncoteColor.PREFIX_INFO + "Removed targeted scene entity.");
            } else {
                selectedSceneEntity.put(player.getUniqueId(), ent.getUniqueId());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.5f);
                player.sendMessage(DragoncoteColor.PREFIX_INFO + "Selected scene entity! Left-click any block with the tool to move it there.");
            }
        }
    }

    @EventHandler
    public void onSceneToolInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!isScenesTool(player.getInventory().getItemInMainHand()) || !player.hasPermission("dccosmetics.admin")) return;

        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            UUID selId = selectedSceneEntity.get(player.getUniqueId());
            if (selId != null) {
                Entity ent = Bukkit.getEntity(selId);
                if (ent != null && ent.isValid()) {
                    event.setCancelled(true);
                    Location dest = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
                    dest.setYaw(ent.getLocation().getYaw());
                    ent.teleport(dest);
                    player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
                    player.sendMessage(DragoncoteColor.PREFIX_SUCCESS + "Moved scene entity to (" + dest.getBlockX() + ", " + dest.getBlockY() + ", " + dest.getBlockZ() + ")!");
                }
            }
        } else if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR && player.isSneaking()) {
            event.setCancelled(true);
            Bukkit.dispatchCommand(player, "dm open scenes_spawner " + player.getName());
        }
    }

    private boolean isSceneEntity(Entity ent) {
        if (ent == null) return false;
        if (activeSceneUuids.contains(ent.getUniqueId().toString())) return true;
        for (String tag : ent.getScoreboardTags()) {
            if (tag.equalsIgnoreCase("dcnpc_scene") || tag.startsWith("scene_model:")) return true;
        }
        String name = ent.getCustomName() != null ? ent.getCustomName().toLowerCase() : "";
        return name.contains("sanctuary") || name.contains("beggar") || name.contains("patron") ||
                name.contains("scribe") || name.contains("agronomist") || name.contains("speeder") ||
                name.contains("angler") || name.contains("workstation") || name.contains("stall");
    }

    private void triggerSceneDialogue(Player player, Entity ent) {
        String modelKey = "";
        for (String tag : ent.getScoreboardTags()) {
            if (tag.startsWith("scene_model:")) {
                modelKey = tag.substring("scene_model:".length()).toLowerCase();
                break;
            }
        }
        if (modelKey.isEmpty() && ent.getCustomName() != null) {
            modelKey = ent.getCustomName().toLowerCase();
        }

        List<String> pool = getQuotesForScene(modelKey);
        String quote = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));

        String wName = player.getWorld().getName();
        boolean isBw = wName.toLowerCase().contains("bw") || wName.toLowerCase().contains("drain") || wName.toLowerCase().contains("void");

        String formattedName = ent.getCustomName() != null ? ent.getCustomName() : "Sanctuary Resident";
        String line = isBw ? DragoncoteColor.stripToGrayscale("&7[" + formattedName + "&7] \"" + quote + "\"") :
                DragoncoteColor.colorize(formattedName + " &f➜ <#E2F6A8>\"" + quote + "\"");

        player.sendMessage(line);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, ent.getLocation().clone().add(0, 1.5, 0), 6, 0.2, 0.3, 0.2, 0.02);
    }

    private List<String> getQuotesForScene(String key) {
        if (key.contains("beggar")) {
            return Arrays.asList(
                    "Spare an emerald, Agent? I blew my last coin on custom particle trails...",
                    "If the timeline collapses again, do my gambling debts get wiped too?",
                    "Corvus told me hard work pays off, but honestly, sitting here has better Wi-Fi.",
                    "Careful near the Tech Forge. Kristoph once accidentally teleported my lunch into the void."
            );
        }
        if (key.contains("mug") || key.contains("patron")) {
            return Arrays.asList(
                    "Cheers! Don't let the void sentinels catch you drinking on duty!",
                    "This brew was fermented in an anti-gravity chamber. It literally hits different!",
                    "One more round of chronomantic ale and I'll jump dimensions with no protective keystone!",
                    "Did you see Corvus walking around? Guy never stops moving. Take a seat and relax!"
            );
        }
        if (key.contains("reader") || key.contains("writer") || key.contains("scribe")) {
            return Arrays.asList(
                    "Shh! I'm on chapter 42: 'How to Prevent Timeline Loops for Dummies'!",
                    "Do you mind? The ink dries faster in this dimension and you're making a draft!",
                    "I'm documenting your journey, Agent. Try not to die in an embarrassing way.",
                    "According to this chronicle, in timeline 7, Corvus had a moustache. Fascinating."
            );
        }
        if (key.contains("swing")) {
            return Arrays.asList(
                    "Wheee! If I kick hard enough, can I breach the stratosphere?!",
                    "Look at me, Corvus! No hands and zero timeline shielding!",
                    "The Emperor can't catch me if my Y-coordinate keeps changing!",
                    "Faster! I think I saw a void portal opening at the peak of the arc!"
            );
        }
        if (key.contains("farmer") || key.contains("agronomist")) {
            return Arrays.asList(
                    "These void-infused pumpkins grow twice as fast when the reactor flares!",
                    "Watch your boots! Freshly fertilized with purified chronomantic mulch!",
                    "The weather here is weird. One day it's snowy, next day everything turns black and white!",
                    "Hands off the prized melons! They’re attuned to the harvest leyline."
            );
        }
        if (key.contains("hoverbike") || key.contains("speeder") || key.contains("hover")) {
            return Arrays.asList(
                    "VRRROOOOM! ...System diagnostics nominal. Anti-gravity thrusters primed for takeoff.",
                    "Warning: Exceeding Mach 2 inside Sanctuary Plaza is strictly prohibited by Command.",
                    "Twin chronomantic turbofans engaged. Please keep all limbs inside the slipstream.",
                    "Zero to supersonic in 1.4 seconds. Helmet not included."
            );
        }
        if (key.contains("fisher") || key.contains("angler")) {
            return Arrays.asList(
                    "The fish aren't biting... probably because they sense an imperial probe in the bayou.",
                    "Hooked a strange glowing clock yesterday. Reeled it in and aged five seconds!",
                    "Keep your voice down! You're scaring away the quantum salmon!",
                    "The marshes whisper of deep currents running under the bedrock."
            );
        }
        if (key.contains("blacksmith")) {
            return Arrays.asList(
                    "Stand back from the quench basin! You'll evaporate into raw steam!",
                    "Hammering chrono-alloys is 10% skill and 90% hoping the anvil doesn't phase through the floor.",
                    "Kristoph says high heat purifies the matrix. My scorched apron agrees."
            );
        }
        return Arrays.asList(
                "Busy day in Sanctuary! Keep your keystone charged, Agent.",
                "Sanctuary stands strong, even when the timeline trembles.",
                "Have you spoken with Corvus yet? Command has orders waiting for you."
        );
    }

    public void deployForgeScenes(Player player) {
        Location base = player.getLocation();
        player.sendMessage(DragoncoteColor.PREFIX_INFO + "Deploying 4 animated Tech Forge workstations...");

        Location l1 = base.clone().add(2.5, 0, 0);
        Location l2 = base.clone().add(-2.5, 0, 0);
        Location l3 = base.clone().add(0, 0, 2.5);
        Location l4 = base.clone().add(0, 0, -2.5);

        spawnSceneModel("scene_blacksmith_1", l1, player);
        spawnSceneModel("scene_blacksmith_2", l2, player);
        spawnSceneModel("scene_blacksmith_3", l3, player);
        spawnSceneModel("scene_blacksmith_4", l4, player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "Deployed Tech Forge Blacksmiths (Anvil, Bellows, Grindstone, Quench) around Kristoph!");
        player.sendMessage("§7Use §f/dcnpc scene tool §7to fine-tune their exact rotation and placement.");
    }

    public void deployMarketScenes(Player player) {
        Location base = player.getLocation();
        player.sendMessage(DragoncoteColor.PREFIX_INFO + "Deploying animated Sanctuary Bazaar stalls...");

        Location lStand = base.clone().add(3, 0, 0);
        Location lBaker = base.clone().add(-3, 0, 0);
        Location lBooks = base.clone().add(0, 0, 3);
        Location lFish  = base.clone().add(0, 0, -3);

        spawnSceneModel("scene_market_1", lStand, player);
        spawnSceneModel("scene_market_2", lBaker, player);
        spawnSceneModel("scene_market_3", lBooks, player);
        spawnSceneModel("scene_market_4", lFish, player);

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f);
        player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "Deployed Market Stalls (1-4) around the Bazaar!");
        player.sendMessage("§7Use §f/dcnpc scene tool §7to adjust stall orientation and positions.");
    }

    public void deployPigeons(Player player) {
        Location base = player.getLocation();
        player.sendMessage(DragoncoteColor.PREFIX_INFO + "Deploying animated Sanctuary Pigeons (Plaza & Rooftops)...");

        spawnSceneModel("scenes_pigeons_ground", base.clone().add(2, 0, 1), player);
        spawnSceneModel("scenes_pigeon", base.clone().add(-2, 0, 2), player);
        spawnSceneModel("scenes_pigeon1", base.clone().add(1, 0, -3), player);
        spawnSceneModel("scenes_pigeons_fly", base.clone().add(0, 4, 0), player);

        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.2f);
        player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "Deployed Pigeons (Ground cluster, individual pigeons, and overhead flock)!");
    }

    public void deployCrows(Player player) {
        Location base = player.getLocation();
        player.sendMessage(DragoncoteColor.PREFIX_INFO + "Deploying desolate Crows across Black & White dimension...");

        spawnSceneModel("scenes_crow", base.clone().add(2, 0, 1), player);
        spawnSceneModel("scenes_crow", base.clone().add(-2, 0, 2), player);
        spawnSceneModel("flying_crows", base.clone().add(0, 6, 0), player);
        spawnSceneModel("flying_crows_wide", base.clone().add(0, 8, 0), player);

        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 1.0f, 0.8f);
        player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "Deployed Crows (Perched crows & circling flocks)!");
    }

    public void deployFishermen(Player player) {
        Location base = player.getLocation();
        player.sendMessage(DragoncoteColor.PREFIX_INFO + "Deploying animated Fishermen around the Docks & Harbor...");

        spawnSceneModel("scene_fisherman_stick", base.clone().add(2, 0, 0), player);
        spawnSceneModel("scene_fisherman_sitting", base.clone().add(-2, 0, 1), player);
        spawnSceneModel("scene_fisherman_standing", base.clone().add(0, 0, 3), player);
        spawnSceneModel("scene_fisherman_boat", base.clone().add(0, -0.2, -4), player);

        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 1.0f);
        player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "Deployed Fishermen (Boat, Fishing Rod, Sitting, Standing) around Finn's Docks!");
        player.sendMessage("§7Use §f/dcnpc scene tool §7to adjust their positions, or right-click them for quirky dialogue!");
    }

    public void untrackScene(UUID entityUuid) {
        if (entityUuid == null) return;
        if (activeSceneUuids.remove(entityUuid.toString())) {
            saveScenes();
        }
    }

    public int purgeScenes(Player player, String category) {
        int removed = 0;
        boolean purgeAll = (category == null || category.equalsIgnoreCase("all") || category.isEmpty());
        double radiusSq = purgeAll ? (500.0 * 500.0) : (50.0 * 50.0);
        Location pLoc = (player != null) ? player.getLocation() : null;

        // 0. Always remove any directly tracked scene entities across loaded worlds
        for (String uuidStr : new ArrayList<>(activeSceneUuids)) {
            try {
                Entity tracked = Bukkit.getEntity(UUID.fromString(uuidStr));
                if (tracked != null && (purgeAll || pLoc == null || (tracked.getWorld().equals(pLoc.getWorld()) && tracked.getLocation().distanceSquared(pLoc) <= radiusSq))) {
                    tracked.remove();
                    removed++;
                }
            } catch (Throwable ignored) {}
        }

        // 1. MythicMobs API Reflection Purge
        try {
            Class<?> mmBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = mmBukkitClass.getMethod("inst").invoke(null);
            Object mobManager = mmBukkitClass.getMethod("getMobManager").invoke(inst);
            Collection<?> activeMobs = (Collection<?>) mobManager.getClass().getMethod("getActiveMobs").invoke(mobManager);
            for (Object activeMob : new ArrayList<>(activeMobs)) {
                Object mobType = activeMob.getClass().getMethod("getType").invoke(activeMob);
                String internalName = ((String) mobType.getClass().getMethod("getInternalName").invoke(mobType)).toLowerCase();
                if (internalName.contains("scene") || internalName.contains("blacksmith") || internalName.contains("market") || internalName.contains("pigeon") || internalName.contains("crow") || internalName.contains("fisher") || internalName.contains("townsfolk") || internalName.contains("hover")) {
                    Object entity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
                    Object bukkitEntity = entity.getClass().getMethod("getBukkitEntity").invoke(entity);
                    if (bukkitEntity instanceof Entity) {
                        Entity be = (Entity) bukkitEntity;
                        if (pLoc == null || (be.getWorld().equals(pLoc.getWorld()) && (purgeAll || be.getLocation().distanceSquared(pLoc) <= radiusSq))) {
                            activeMob.getClass().getMethod("despawn").invoke(activeMob);
                            be.remove();
                            removed++;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. World Entity Fallback Purge
        if (player != null) {
            for (Entity ent : player.getWorld().getEntities()) {
                if (!purgeAll && ent.getLocation().distanceSquared(pLoc) > radiusSq) continue;
                if (ent instanceof Player || ent.hasMetadata("NPC")) continue;

                String name = ent.getCustomName() != null ? ent.getCustomName().toLowerCase() : "";
                Set<String> tags = ent.getScoreboardTags();
                boolean tagMatch = false;
                for (String t : tags) {
                    String tl = t.toLowerCase();
                    if (tl.contains("scene") || tl.contains("meg") || tl.contains("mythic") || tl.contains("modelengine") || tl.contains("hover")) {
                        tagMatch = true;
                        break;
                    }
                }

                boolean isPawn = (ent.getType() == EntityType.PIG ||
                        ent.getType() == EntityType.ARMOR_STAND ||
                        ent.getType().name().contains("DISPLAY") ||
                        ent.getType().name().contains("INTERACTION"));

                boolean isTracked = activeSceneUuids.contains(ent.getUniqueId().toString());

                if (isTracked || tagMatch || (isPawn && !ent.hasGravity() && (name.contains("scene") || name.contains("sanctuary") || name.contains("fisherman") || name.contains("angler") || name.contains("blacksmith") || name.contains("market")))) {
                    ent.remove();
                    removed++;
                }
            }
            activeSceneUuids.clear();
            saveScenes();
            player.sendMessage(DragoncoteColor.PREFIX_INFO + "Purged §f" + removed + " §7scene entities in radius.");
        }
        return removed;
    }

    public void loadScenes() {
        if (!scenesFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(scenesFile);
        activeSceneUuids.clear();
        activeSceneUuids.addAll(cfg.getStringList("active_uuids"));
    }

    private void saveScenes() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("active_uuids", activeSceneUuids);
        try {
            cfg.save(scenesFile);
        } catch (Exception ignored) {}
    }
}
