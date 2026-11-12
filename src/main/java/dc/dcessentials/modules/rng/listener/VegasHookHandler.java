package dc.dcessentials.modules.rng.listener;

import com.github.Shimado.vegasapi.VegasAPI;
import com.github.Shimado.vegasapi.api.VictoryUtil;
import com.github.Shimado.vegasapi.instances.CasinoBet;
import com.github.Shimado.vegasapi.instances.CasinoGameMode;
import com.github.Shimado.vegasapi.interfaces.PlacedCasinoTable;
import dc.dccosmetics.DCCosmetics;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class VegasHookHandler {

    public static void register() {
        try {
            VictoryUtil current = VegasAPI.getVictoryUtil();
            if (!(current instanceof VegasVictoryHook)) {
                VegasAPI.setVictoryUtil(new VegasVictoryHook(current));
                DCCosmetics.getInstance().getLogger().info("[DCEssentials] Hooked into Vegas VictoryUtil (3x Win, 1x Defeat RNG progression active; chip buying exploit disabled).");
            }
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().warning("[DCEssentials] Could not hook into Vegas VictoryUtil: " + t.getMessage());
        }

        try {
            VegasPlaceholderTracker.start();
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().warning("[DCEssentials] Could not start Vegas Placeholder Tracker: " + t.getMessage());
        }
    }

    public static class VegasVictoryHook implements VictoryUtil {

        private final VictoryUtil delegate;

        public VegasVictoryHook(VictoryUtil delegate) {
            this.delegate = delegate;
        }

        private void handleProgression(Player player, CasinoBet bet, List<CasinoBet> bets, boolean won) {
            if (player == null || !player.isOnline()) return;

            double money = 0.0;
            int chips = 0;

            if (bet != null) {
                money += bet.getMoneyBet();
                if (bet.getChipsBet() != null) chips += bet.getChipsBet().getAmount();
            }
            if (bets != null) {
                for (CasinoBet b : bets) {
                    if (b != null) {
                        money += b.getMoneyBet();
                        if (b.getChipsBet() != null) chips += b.getChipsBet().getAmount();
                    }
                }
            }

            int baseXp = (int) Math.max(30, Math.min(300, Math.round(30 + money * 0.15 + chips * 2)));
            int finalXp = won ? (baseXp * 3) : baseXp;
            double boost = won ? 25.0 : 0.0;

            DCCosmetics.getInstance().getRngManager().processAction(player, "casino", boost, finalXp, "casino", null);
        }

        @Override
        public void victory(Player player, CasinoBet bet, CasinoGameMode gameMode, double winAmount, Location loc, UUID tableUuid) {
            if (delegate != null) delegate.victory(player, bet, gameMode, winAmount, loc, tableUuid);
            handleProgression(player, bet, null, true);
        }

        @Override
        public void victory(Player player, CasinoBet bet, CasinoGameMode gameMode, double winAmount, PlacedCasinoTable table) {
            if (delegate != null) delegate.victory(player, bet, gameMode, winAmount, table);
            handleProgression(player, bet, null, true);
        }

        @Override
        public void victory(Player player, CasinoBet bet, List<CasinoBet> bets, CasinoGameMode gameMode, Location loc) {
            if (delegate != null) delegate.victory(player, bet, bets, gameMode, loc);
            handleProgression(player, bet, bets, true);
        }

        @Override
        public void victory(Player player, CasinoBet bet, List<CasinoBet> bets, CasinoGameMode gameMode, PlacedCasinoTable table) {
            if (delegate != null) delegate.victory(player, bet, bets, gameMode, table);
            handleProgression(player, bet, bets, true);
        }

        @Override
        public void bonus(Player player, CasinoBet bet, CasinoGameMode gameMode, double winAmount, Location loc, UUID tableUuid) {
            if (delegate != null) delegate.bonus(player, bet, gameMode, winAmount, loc, tableUuid);
            handleProgression(player, bet, null, true);
        }

        @Override
        public void bonus(Player player, CasinoBet bet, CasinoGameMode gameMode, double winAmount, PlacedCasinoTable table) {
            if (delegate != null) delegate.bonus(player, bet, gameMode, winAmount, table);
            handleProgression(player, bet, null, true);
        }

        @Override
        public void defeat(Player player, CasinoBet bet, CasinoGameMode gameMode, Location loc, UUID tableUuid) {
            if (delegate != null) delegate.defeat(player, bet, gameMode, loc, tableUuid);
            handleProgression(player, bet, null, false);
        }

        @Override
        public void defeat(Player player, CasinoBet bet, CasinoGameMode gameMode, PlacedCasinoTable table) {
            if (delegate != null) delegate.defeat(player, bet, gameMode, table);
            handleProgression(player, bet, null, false);
        }

        @Override
        public void defeat(Player player, CasinoBet bet, CasinoGameMode gameMode, double lostAmount, Location loc, UUID tableUuid) {
            if (delegate != null) delegate.defeat(player, bet, gameMode, lostAmount, loc, tableUuid);
            handleProgression(player, bet, null, false);
        }

        @Override
        public void defeat(Player player, CasinoBet bet, CasinoGameMode gameMode, double lostAmount, PlacedCasinoTable table) {
            if (delegate != null) delegate.defeat(player, bet, gameMode, lostAmount, table);
            handleProgression(player, bet, null, false);
        }

        @Override
        public void draw(Player player, CasinoBet bet, CasinoGameMode gameMode) {
            if (delegate != null) delegate.draw(player, bet, gameMode);
            handleProgression(player, bet, null, false);
        }
    }
}
