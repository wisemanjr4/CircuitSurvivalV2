package net.circuitsurvival.recipes;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecipeKeyRegistry {

    private static final Set<NamespacedKey> keys = new HashSet<>();

    public static void register(NamespacedKey key) {
        if (key != null) keys.add(key);
    }

    public static List<NamespacedKey> getAllKeys() {
        return new ArrayList<>(keys);
    }

    public static void populateFromIterator(Plugin plugin) {
        String ns = new NamespacedKey(plugin, "_discover_check").getNamespace();
        java.util.Iterator<org.bukkit.inventory.Recipe> it = plugin.getServer().recipeIterator();
        while (it.hasNext()) {
            org.bukkit.inventory.Recipe r = it.next();
            if (r instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals(ns))
                keys.add(k.getKey());
        }
    }
}
