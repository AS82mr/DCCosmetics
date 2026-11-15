package dc.dcessentials.modules.teleportation;

import org.bukkit.Location;

import java.util.UUID;

public class KeystoneProfile {
    public static final int MICRO_PER_CHARGE = 1000;
    public static final int ABSOLUTE_MAX_ENERGY = 800;
    public static final int DEFAULT_OVERLOAD_CLEAR_BELOW = 50;

    private final UUID uuid;
    private final long createdAtMs = System.currentTimeMillis();

    private volatile KeystoneTier tier = KeystoneTier.NONE;
    private volatile int energy = 0;
    private volatile int micro = 0;
    private volatile int heat = 0;
    private volatile boolean overloaded = false;
    private volatile long newbieShieldUntil = 0;
    private volatile long updatedAt = 0;
    private volatile boolean loaded = false;
    private volatile boolean dirty = false;

    private volatile int consensualTpaCount = 0;
    private volatile long lastCryoTime = 0;
    private volatile long lastPhaseVent = 0;
    private volatile long lastNexusIncursion = 0;
    private volatile Location backLocation = null;

    public KeystoneProfile(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() { return uuid; }
    public long getCreatedAtMs() { return createdAtMs; }

    public boolean isLoaded() { return loaded; }
    public synchronized void setLoaded(boolean loaded) { this.loaded = loaded; }

    private void markDirty() {
        this.updatedAt = System.currentTimeMillis();
        this.dirty = true;
    }

    public synchronized void applySnapshot(KeystoneSnapshot snapshot) {
        if (snapshot == null) {
            this.loaded = true;
            return;
        }
        this.tier = snapshot.tier() != null ? snapshot.tier() : KeystoneTier.NONE;
        this.energy = Math.max(0, snapshot.energy());
        this.micro = Math.max(0, Math.min(MICRO_PER_CHARGE - 1, snapshot.micro()));
        this.heat = Math.max(0, Math.min(100, snapshot.heat()));
        this.overloaded = snapshot.overloaded() || this.heat >= 100;
        this.newbieShieldUntil = snapshot.shieldUntil();
        this.updatedAt = snapshot.updatedAt();
        this.loaded = true;
        this.dirty = false;
    }

    public synchronized KeystoneSnapshot toSnapshot() {
        return new KeystoneSnapshot(
                uuid,
                tier,
                energy,
                micro,
                heat,
                overloaded,
                newbieShieldUntil,
                updatedAt > 0 ? updatedAt : System.currentTimeMillis()
        );
    }

    public KeystoneTier getTier() { return tier; }

    /**
     * X1: setTier never lowers or wipes energy.
     */
    public synchronized void setTier(KeystoneTier tier) {
        KeystoneTier next = tier != null ? tier : KeystoneTier.NONE;
        if (this.tier != next) {
            this.tier = next;
            markDirty();
        }
    }

    /**
     * Trims energy down to the current tier's max (or effectiveMax) ONLY when attuned,
     * returning the overflow charges so the caller can hand them back as physical cubes.
     * When tier == NONE, energy is parked and 0 is returned.
     */
    public synchronized int trimToTier() {
        return trimToTier(tier.getMaxEnergy());
    }

    public synchronized int trimToTier(int effectiveMax) {
        if (tier == KeystoneTier.NONE || effectiveMax <= 0) {
            return 0;
        }
        int cap = Math.min(ABSOLUTE_MAX_ENERGY, effectiveMax);
        if (this.energy > cap) {
            int overflow = this.energy - cap;
            this.energy = cap;
            markDirty();
            return overflow;
        }
        return 0;
    }

    public int getEnergy() { return energy; }

    public int usableEnergy() {
        return tier == KeystoneTier.NONE ? 0 : energy;
    }

    public int parkedEnergy() {
        return tier == KeystoneTier.NONE ? energy : 0;
    }

    public int getMicro() { return micro; }

    public synchronized void setMicro(int micro) {
        this.micro = Math.max(0, Math.min(MICRO_PER_CHARGE - 1, micro));
        markDirty();
    }

    public long getTotalMicro() {
        if (tier == KeystoneTier.NONE) return 0L;
        return (long) energy * MICRO_PER_CHARGE + micro;
    }

    /**
     * Non-destructive energy setter (X1: does not clamp to 0 when tier is NONE).
     */
    public synchronized void setEnergy(int energy) {
        int cap = tier != KeystoneTier.NONE ? Math.max(tier.getMaxEnergy(), this.energy) : ABSOLUTE_MAX_ENERGY;
        this.energy = Math.max(0, Math.min(cap, energy));
        markDirty();
    }

    public synchronized int addCharges(int n) {
        return addCharges(n, tier != KeystoneTier.NONE ? tier.getMaxEnergy() : ABSOLUTE_MAX_ENERGY);
    }

    public synchronized int addCharges(int n, int effectiveMax) {
        if (n <= 0) return 0;
        int cap = effectiveMax > 0 ? Math.min(ABSOLUTE_MAX_ENERGY, effectiveMax)
                : (tier != KeystoneTier.NONE ? tier.getMaxEnergy() : ABSOLUTE_MAX_ENERGY);
        int space = Math.max(0, cap - this.energy);
        int accepted = Math.min(n, space);
        if (accepted > 0) {
            this.energy += accepted;
            markDirty();
        }
        return accepted;
    }

    public synchronized int removeCharges(int n) {
        if (n <= 0 || this.energy <= 0) return 0;
        int removed = Math.min(n, this.energy);
        this.energy -= removed;
        markDirty();
        return removed;
    }

    public synchronized void addEnergy(int amount) {
        if (amount > 0) {
            addCharges(amount);
        } else if (amount < 0) {
            removeCharges(-amount);
        }
    }

    public synchronized boolean consumeEnergy(int amount) {
        if (amount <= 0) return true;
        if (usableEnergy() >= amount) {
            this.energy -= amount;
            markDirty();
            return true;
        }
        return false;
    }

    /**
     * Authoritative Microcharge spend algorithm (Section 5.4).
     */
    public synchronized SpendResult spendMicro(int cost, int overdrawHeatBase, double heatMult) {
        return spendMicro(cost, overdrawHeatBase, heatMult, DEFAULT_OVERLOAD_CLEAR_BELOW);
    }

    public synchronized SpendResult spendMicro(int cost, int overdrawHeatBase, double heatMult, int overloadClearBelow) {
        cost = Math.max(0, cost);
        boolean attuned = (tier != KeystoneTier.NONE);
        int cracked = 0;

        if (attuned) {
            while (this.micro < cost && this.energy > 0) {
                this.energy -= 1;
                this.micro += MICRO_PER_CHARGE;
                cracked++;
            }
        }

        int paid = attuned ? Math.min(this.micro, cost) : 0;
        if (attuned) {
            this.micro -= paid;
        }

        int shortfall = cost - paid;
        boolean overdraw = shortfall > 0;
        int heatGain = 0;
        if (overdraw && cost > 0 && overdrawHeatBase > 0 && heatMult > 0.0) {
            heatGain = Math.max(1, (int) Math.round(overdrawHeatBase * ((double) shortfall / cost) * heatMult));
            addHeatInternal(heatGain, overloadClearBelow);
        }

        // Enforce 0 <= micro <= 999 invariant
        if (this.micro >= MICRO_PER_CHARGE) {
            int carry = this.micro / MICRO_PER_CHARGE;
            this.micro %= MICRO_PER_CHARGE;
            int cap = attuned ? Math.max(tier.getMaxEnergy(), this.energy) : ABSOLUTE_MAX_ENERGY;
            this.energy = Math.min(cap, this.energy + carry);
        } else if (this.micro < 0) {
            this.micro = 0;
        }

        if (cracked > 0 || paid > 0 || heatGain > 0) {
            markDirty();
        }

        return new SpendResult(
                true,
                cost,
                cracked,
                paid,
                shortfall,
                overdraw,
                heatGain,
                this.energy,
                this.micro,
                this.heat,
                this.overloaded
        );
    }

    public synchronized void refundMicro(int m) {
        refundMicro(m, tier.getMaxEnergy());
    }

    public synchronized void refundMicro(int m, int effectiveMax) {
        if (m <= 0) return;
        this.micro += m;
        if (this.micro >= MICRO_PER_CHARGE) {
            int carry = this.micro / MICRO_PER_CHARGE;
            this.micro %= MICRO_PER_CHARGE;
            int max = effectiveMax > 0 ? Math.min(ABSOLUTE_MAX_ENERGY, effectiveMax) : tier.getMaxEnergy();
            if (max > 0 && this.energy < max) {
                this.energy = Math.min(max, this.energy + carry);
            }
        }
        markDirty();
    }

    public int getHeat() { return heat; }

    public synchronized void setHeat(int heat) {
        setHeat(heat, DEFAULT_OVERLOAD_CLEAR_BELOW);
    }

    public synchronized void setHeat(int heat, int overloadClearBelow) {
        int clamped = Math.max(0, Math.min(100, heat));
        this.heat = clamped;
        if (clamped >= 100) {
            this.overloaded = true;
        } else if (clamped <= overloadClearBelow) {
            this.overloaded = false;
        }
        markDirty();
    }

    private void addHeatInternal(int delta, int overloadClearBelow) {
        setHeat(this.heat + delta, overloadClearBelow);
    }

    public synchronized void addHeat(int delta) {
        addHeatInternal(delta, DEFAULT_OVERLOAD_CLEAR_BELOW);
    }

    public synchronized void reduceHeat(int delta) {
        reduceHeat(delta, DEFAULT_OVERLOAD_CLEAR_BELOW);
    }

    public synchronized void reduceHeat(int delta, int overloadClearBelow) {
        setHeat(this.heat - delta, overloadClearBelow);
    }

    public boolean isOverloaded() { return overloaded; }
    public synchronized void setOverloaded(boolean overloaded) {
        this.overloaded = overloaded;
        markDirty();
    }

    public String getStability() {
        if (overloaded || heat >= 100) return "&4&lOVERLOADED";
        return heat >= 80 ? "&cUNSTABLE" : "&aSTABLE";
    }

    public long getNewbieShieldUntil() { return newbieShieldUntil; }
    public synchronized void setNewbieShieldUntil(long until) {
        this.newbieShieldUntil = until;
        markDirty();
    }

    public boolean hasNewbieShield() {
        return System.currentTimeMillis() < newbieShieldUntil;
    }

    public int getConsensualTpaCount() { return consensualTpaCount; }
    public synchronized void incrementTpaCount() { this.consensualTpaCount++; }
    public synchronized void resetTpaCount() { this.consensualTpaCount = 0; }

    public long getLastCryoTime() { return lastCryoTime; }
    public synchronized void setLastCryoTime(long lastCryoTime) { this.lastCryoTime = lastCryoTime; }

    public long getLastPhaseVent() { return lastPhaseVent; }
    public synchronized void setLastPhaseVent(long lastPhaseVent) { this.lastPhaseVent = lastPhaseVent; }

    public long getLastNexusIncursion() { return lastNexusIncursion; }
    public synchronized void setLastNexusIncursion(long lastNexusIncursion) { this.lastNexusIncursion = lastNexusIncursion; }

    public Location getBackLocation() { return backLocation; }
    public synchronized void setBackLocation(Location backLocation) { this.backLocation = backLocation; }

    public long getUpdatedAt() { return updatedAt; }
    public boolean isDirty() { return dirty; }
    public synchronized void setDirty(boolean dirty) { this.dirty = dirty; }
}
