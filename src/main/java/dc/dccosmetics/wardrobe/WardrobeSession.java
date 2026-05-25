package dc.dccosmetics.wardrobe;

import dc.dccosmetics.wardrobe.camera.CameraController;
import dc.dccosmetics.wardrobe.camera.CctvCameraRig;
import dc.dccosmetics.wardrobe.hud.WardrobeHUD;
import dc.dccosmetics.wardrobe.npc.WardrobeMannequin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public class WardrobeSession {
    
    public enum MenuLevel {
        CATEGORIES,
        RARITIES,
        COSMETICS,
        ACTION
    }

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
    
    private final java.util.List<String> categoriesList = java.util.Arrays.asList("Head", "Chest", "Waist", "Boots", "Sword");
    private java.util.List<String> currentRaritiesList = new java.util.ArrayList<>();
    private java.util.List<dc.dccosmetics.model.CosmeticTemplate> currentCosmeticsList = new java.util.ArrayList<>();
    
    private String selectedCategory = "head";
    private String selectedRarity = null;
    private String previewingCosmeticId = null;
    private boolean inCinematic = true;
    private Location currentCameraLocation = null;

    public WardrobeSession(Player player, Location previousLocation) {
        this.playerUuid = player.getUniqueId();
        this.player = player;
        this.previousLocation = previousLocation;
        this.previousGameMode = player.getGameMode();
    }

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
    
    public java.util.List<String> getCategoriesList() { return categoriesList; }

    public java.util.List<String> getCurrentRaritiesList() { return currentRaritiesList; }
    public void setCurrentRaritiesList(java.util.List<String> currentRaritiesList) { this.currentRaritiesList = currentRaritiesList; }
    
    public java.util.List<dc.dccosmetics.model.CosmeticTemplate> getCurrentCosmeticsList() { return currentCosmeticsList; }
    public void setCurrentCosmeticsList(java.util.List<dc.dccosmetics.model.CosmeticTemplate> currentCosmeticsList) { this.currentCosmeticsList = currentCosmeticsList; }

    public Location getCurrentCameraLocation() { return currentCameraLocation; }
    public void setCurrentCameraLocation(Location currentCameraLocation) { this.currentCameraLocation = currentCameraLocation; }

    public void cleanup() {
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
