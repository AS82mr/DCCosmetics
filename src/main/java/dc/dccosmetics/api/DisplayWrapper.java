package dc.dccosmetics.api;

import org.bukkit.entity.Player;
import org.joml.Vector3f;
import java.util.List;

/**
 * A version-independent wrapper for a single Display Entity packet.
 */
public interface DisplayWrapper {

    void setScale(Vector3f scale);
    void setTranslation(Vector3f translation);
    void setRotation(Vector3f rotation);
    void setColor(String hexColor);

    void mount(Player target);
    void unmount(Player target);

    void update();
    void destroy();
}