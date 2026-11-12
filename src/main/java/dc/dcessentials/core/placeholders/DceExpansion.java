package dc.dcessentials.core.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DceExpansion extends PlaceholderExpansion {

    private final DCEssentialsExpansion delegate;

    public DceExpansion(DCEssentialsExpansion delegate) {
        this.delegate = delegate;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dce";
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
        return delegate.onPlaceholderRequest(player, params);
    }
}
