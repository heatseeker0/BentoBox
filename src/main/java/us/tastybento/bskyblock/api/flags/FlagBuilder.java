package us.tastybento.bskyblock.api.flags;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.event.Listener;

import org.bukkit.inventory.ItemStack;
import us.tastybento.bskyblock.api.panels.PanelItem;
import us.tastybento.bskyblock.api.panels.builders.PanelItemBuilder;

public class FlagBuilder {

    private String id;
    private PanelItem icon;
    private Optional<Listener> listener = Optional.empty();

    public FlagBuilder id(String id) {
        this.id = id;
        return this;
    }

    public FlagBuilder icon(Material icon) {
        icon(new PanelItemBuilder().icon(new ItemStack(icon)).build());
        return this;
    }

    public FlagBuilder icon(PanelItem icon) {
        this.icon = icon;
        //TODO: if icon don't have a clickhandler, add the default one
        //TODO: if icon don't have a display name, set it to the default reference
        //TODO: if icon don't have a lore, set it to the default one
        return this;
    }

    public FlagBuilder listener(Listener listener) {
        this.listener = Optional.of(listener);
        return this;
    }

    public Flag build() {
        return new Flag(id, icon, listener);
    }
}