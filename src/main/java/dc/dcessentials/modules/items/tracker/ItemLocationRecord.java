package dc.dcessentials.modules.items.tracker;

import java.util.UUID;

public class ItemLocationRecord {

    public enum LocationType {
        INVENTORY,
        CHEST,
        SHULKER,
        PLAYER_VAULT
    }

    private final String itemId;
    private final String instanceUuid;
    private final UUID lastUserUuid;
    private final String lastUserName;
    private final LocationType locationType;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int vaultNumber;
    private int amount;
    private long lastSeenTimestamp;

    public ItemLocationRecord(String itemId, String instanceUuid, UUID lastUserUuid, String lastUserName,
                              LocationType locationType, String world, int x, int y, int z,
                              int vaultNumber, int amount, long lastSeenTimestamp) {
        this.itemId = itemId;
        this.instanceUuid = instanceUuid;
        this.lastUserUuid = lastUserUuid;
        this.lastUserName = lastUserName;
        this.locationType = locationType;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.vaultNumber = vaultNumber;
        this.amount = amount;
        this.lastSeenTimestamp = lastSeenTimestamp;
    }

    public String getItemId() { return itemId; }
    public String getInstanceUuid() { return instanceUuid; }
    public UUID getLastUserUuid() { return lastUserUuid; }
    public String getLastUserName() { return lastUserName; }
    public LocationType getLocationType() { return locationType; }
    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getVaultNumber() { return vaultNumber; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public long getLastSeenTimestamp() { return lastSeenTimestamp; }
    public void setLastSeenTimestamp(long ts) { this.lastSeenTimestamp = ts; }
}
