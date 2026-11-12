package dc.dccosmetics.wardrobe;

import dc.dccosmetics.wardrobe.camera.CameraController;
import dc.dccosmetics.wardrobe.camera.CctvCameraRig;
import dc.dccosmetics.wardrobe.hud.WardrobeHUD;
import dc.dccosmetics.wardrobe.npc.WardrobeMannequin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import dc.dccosmetics.DCCosmetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class WardrobeSession {
    
    public enum MenuLevel {
        CATEGORIES,
        RARITIES,
        COSMETICS,
        ACTION
    }

    private final WardrobeManager manager;
    private final UUID playerUuid;
    private final Player player;
    private final Location previousLocation;
    private final org.bukkit.GameMode previousGameMode;
    
    private WardrobeMannequin mannequin;
    private WardrobeHUD hud;
    private CameraController cameraController;
    private CctvCameraRig   cctvRig;
    
    private MenuLevel currentLevel = MenuLevel.CATEGORIES;
    private int selectedIndex = 0;
    private int scrollWindowOffset = 0;
    
    private List<String> categoriesList = new ArrayList<>();
    private List<String> currentRaritiesList = new ArrayList<>();
    private List<dc.dccosmetics.model.CosmeticTemplate> currentCosmeticsList = new ArrayList<>();
    private List<dc.dccosmetics.wardrobe.teleport.WardrobeTeleportEffect> currentTeleportList = new ArrayList<>();
    
    private String selectedCategory = "head";
    private String selectedRarity = null;
    private String previewingCosmeticId = null;
    private String previewingTeleportId = null;
    private String selectedRarityFilter = "all";
    private int currentUiPage = 0;
    private dc.dccosmetics.wardrobe.teleport.WardrobeTeleportEffect selectedTeleportEffect = null;
    private dc.dccosmetics.model.CosmeticTemplate selectedCosmeticTemplate = null;
    private boolean inCinematic = true;
    private Location currentCameraLocation = null;
    private org.bukkit.inventory.ItemStack[] savedArmor = null;
    private org.bukkit.inventory.ItemStack[] savedInventory = null;
    private org.bukkit.inventory.ItemStack savedOffHand = null;
    private int savedHeldSlot = 0;
    private volatile boolean inTpPreview = false;

    public WardrobeSession(WardrobeManager manager, Player player, Location previousLocation) {
        this.manager = manager;
        this.playerUuid = player.getUniqueId();
        this.player = player;
        this.previousLocation = previousLocation;
        this.previousGameMode = player.getGameMode();
        if (manager != null && manager.getEnabledCategories() != null && !manager.getEnabledCategories().isEmpty()) {
            this.categoriesList = new ArrayList<>(manager.getEnabledCategories());
            this.selectedCategory = this.categoriesList.get(0).toLowerCase();
        } else {
            this.categoriesList = new ArrayList<>(Arrays.asList("Head", "Chest", "Boots"));
            this.selectedCategory = "head";
        }
    }

    public WardrobeSession(Player player, Location previousLocation) {
        this(null, player, previousLocation);
    }

    public WardrobeManager getManager() { return manager; }
    public UUID getPlayerUuid() { return playerUuid; }
    public Player getPlayer() { return player; }
    public Location getPreviousLocation() { return previousLocation; }
    public org.bukkit.GameMode getPreviousGameMode() { return previousGameMode; }

    public WardrobeMannequin getMannequin() { return mannequin; }
    public void setMannequin(WardrobeMannequin mannequin) { this.mannequin = mannequin; }

    public WardrobeHUD getHud() { return hud; }
    public void setHud(WardrobeHUD hud) { this.hud = hud; }
    
    public CameraController getCameraController() { return cameraController; }
    public void setCameraController(CameraController cameraController) { this.cameraController = cameraController; }

    public CctvCameraRig getCctvRig() { return cctvRig; }
    public void setCctvRig(CctvCameraRig cctvRig) { this.cctvRig = cctvRig; }

    public String getSelectedCategory() { return selectedCategory; }
    public void setSelectedCategory(String selectedCategory) { this.selectedCategory = selectedCategory; }

    public String getSelectedRarity() { return selectedRarity; }
    public void setSelectedRarity(String selectedRarity) { this.selectedRarity = selectedRarity; }

    public String getPreviewingCosmeticId() { return previewingCosmeticId; }
    public void setPreviewingCosmeticId(String previewingCosmeticId) { this.previewingCosmeticId = previewingCosmeticId; }

    public boolean isInCinematic() { return inCinematic; }
    public void setInCinematic(boolean inCinematic) { this.inCinematic = inCinematic; }
    
    public MenuLevel getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(MenuLevel currentLevel) { this.currentLevel = currentLevel; }
    
    public int getSelectedIndex() { return selectedIndex; }
    public void setSelectedIndex(int selectedIndex) { this.selectedIndex = selectedIndex; }
    
    public int getScrollWindowOffset() { return scrollWindowOffset; }
    public void setScrollWindowOffset(int scrollWindowOffset) { this.scrollWindowOffset = scrollWindowOffset; }
    
    public List<String> getCategoriesList() { return categoriesList; }
    public void setCategoriesList(List<String> categoriesList) { this.categoriesList = categoriesList; }

    public List<String> getCurrentRaritiesList() { return currentRaritiesList; }
    public void setCurrentRaritiesList(List<String> currentRaritiesList) { this.currentRaritiesList = currentRaritiesList; }
    
    public List<dc.dccosmetics.model.CosmeticTemplate> getCurrentCosmeticsList() { return currentCosmeticsList; }
    public void setCurrentCosmeticsList(List<dc.dccosmetics.model.CosmeticTemplate> currentCosmeticsList) { this.currentCosmeticsList = currentCosmeticsList; }

    public List<dc.dccosmetics.wardrobe.teleport.WardrobeTeleportEffect> getCurrentTeleportList() { return currentTeleportList; }
    public void setCurrentTeleportList(List<dc.dccosmetics.wardrobe.teleport.WardrobeTeleportEffect> currentTeleportList) { this.currentTeleportList = currentTeleportList; }

    public Location getCurrentCameraLocation() { return currentCameraLocation; }
    public void setCurrentCameraLocation(Location currentCameraLocation) { this.currentCameraLocation = currentCameraLocation; }

    public org.bukkit.inventory.ItemStack[] getSavedArmor() { return savedArmor; }
    public void setSavedArmor(org.bukkit.inventory.ItemStack[] savedArmor) { this.savedArmor = savedArmor; }

    public org.bukkit.inventory.ItemStack[] getSavedInventory() { return savedInventory; }
    public void setSavedInventory(org.bukkit.inventory.ItemStack[] savedInventory) { this.savedInventory = savedInventory; }

    public org.bukkit.inventory.ItemStack getSavedOffHand() { return savedOffHand; }
    public void setSavedOffHand(org.bukkit.inventory.ItemStack savedOffHand) { this.savedOffHand = savedOffHand; }

    public int getSavedHeldSlot() { return savedHeldSlot; }
    public void setSavedHeldSlot(int savedHeldSlot) { this.savedHeldSlot = savedHeldSlot; }

    public boolean isInTpPreview() { return inTpPreview; }
    public void setInTpPreview(boolean inTpPreview) { this.inTpPreview = inTpPreview; }

    public String getPreviewingTeleportId() { return previewingTeleportId; }
    public void setPreviewingTeleportId(String previewingTeleportId) { this.previewingTeleportId = previewingTeleportId; }

    public String getSelectedRarityFilter() { return selectedRarityFilter; }
    public void setSelectedRarityFilter(String selectedRarityFilter) { this.selectedRarityFilter = selectedRarityFilter; }

    public int getCurrentUiPage() { return currentUiPage; }
    public void setCurrentUiPage(int currentUiPage) { this.currentUiPage = currentUiPage; }

    public dc.dccosmetics.wardrobe.teleport.WardrobeTeleportEffect getSelectedTeleportEffect() { return selectedTeleportEffect; }
    public void setSelectedTeleportEffect(dc.dccosmetics.wardrobe.teleport.WardrobeTeleportEffect selectedTeleportEffect) { this.selectedTeleportEffect = selectedTeleportEffect; }

    public dc.dccosmetics.model.CosmeticTemplate getSelectedCosmeticTemplate() { return selectedCosmeticTemplate; }
    public void setSelectedCosmeticTemplate(dc.dccosmetics.model.CosmeticTemplate selectedCosmeticTemplate) { this.selectedCosmeticTemplate = selectedCosmeticTemplate; }

    public void cleanup() {
        // Ensure the player is safely grounded before unlocking the camera
        if (previousLocation != null) {
            player.teleport(previousLocation);
            DCCosmetics.getInstance().getLogger().info("[Wardrobe] Teleported " +
                    player.getName() + " back to previous location (ground) during cleanup.");
        }
        if (hud != null) {
            hud.destroy();
        }
        if (mannequin != null) {
            mannequin.destroy();
        }
        if (cameraController != null) {
            cameraController.unlockCamera(player);
        }
        if (cctvRig != null) {
            cctvRig.destroy();
        }
    }
}
