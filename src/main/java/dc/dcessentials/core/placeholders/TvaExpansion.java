package dc.dcessentials.core.placeholders;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TvaExpansion extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "tva";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Dragoncote";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.1";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        KeystoneProfile profile = DCCosmetics.getInstance().getTeleportationManager().getProfile(player);
        String pLower = params.toLowerCase();

        switch (pLower) {
            case "stability":
                return profile.getStability();
            case "tier":
                return profile.getTier().name();
            case "keystone_name":
                return profile.getTier().getKeystoneName();
            case "heat":
                return String.valueOf(profile.getHeat());
            case "overloaded":
            case "keystone_overloaded":
                return String.valueOf(profile.isOverloaded());
            case "energy":
            case "tesseract":
                return String.valueOf(profile.getEnergy());
            case "keystone_parked":
                return String.valueOf(profile.parkedEnergy());
            case "microcharges":
            case "keystone_micro":
                return String.valueOf(profile.getMicro());
            case "tesseract_exact":
                return String.format(java.util.Locale.US, "%d.%03d", profile.usableEnergy(), profile.getMicro());
            case "tesseract_total_micro":
                return String.valueOf(profile.getTotalMicro());
            case "max":
                return String.valueOf(DCCosmetics.getInstance().getTeleportationManager().getEnergyService().getEffectiveMaxEnergy(player, profile));
            case "keystone_efficiency":
            case "keystone_insulation":
                return profile.getTier().getEfficiencyText();
            default:
                return null;
        }
    }
}
