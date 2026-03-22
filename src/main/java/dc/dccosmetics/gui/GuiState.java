package dc.dccosmetics.gui;

public class GuiState {
    private String selectedRarity = "uncommon";
    private String selectedType = "head";
    private String viewedCosmeticId = null; // The cosmetic they clicked to view colors
    private long lastAccessedTime;

    public GuiState() {
        touch();
    }

    public void touch() {
        this.lastAccessedTime = System.currentTimeMillis();
    }

    public boolean isExpired() {
        // 10 minutes = 600,000 milliseconds
        return (System.currentTimeMillis() - lastAccessedTime) > 600000;
    }

    public String getSelectedRarity() { return selectedRarity; }
    public void setSelectedRarity(String selectedRarity) { this.selectedRarity = selectedRarity; touch(); }

    public String getSelectedType() { return selectedType; }
    public void setSelectedType(String selectedType) { this.selectedType = selectedType; touch(); }

    public String getViewedCosmeticId() { return viewedCosmeticId; }
    public void setViewedCosmeticId(String viewedCosmeticId) { this.viewedCosmeticId = viewedCosmeticId; touch(); }
}