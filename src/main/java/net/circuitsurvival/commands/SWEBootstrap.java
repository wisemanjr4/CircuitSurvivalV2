package net.circuitsurvival.commands;

import net.circuitsurvival.CircuitSurvivalPlugin;
import net.circuitsurvival.listeners.WandListener;
import net.circuitsurvival.managers.SelectionManager;
import org.bukkit.plugin.PluginManager;

/**
 * SWE (サバイバルWorldEdit) の結合処理。
 * CircuitSurvivalPlugin からはリフレクションで呼ばれるため、
 * このクラスをビルドから除外すれば SWE なし版になる。
 */
public final class SWEBootstrap {

    private static SelectionManager selectionManager;

    private SWEBootstrap() {}

    public static void init(CircuitSurvivalPlugin plugin) {
        selectionManager = new SelectionManager();
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new WandListener(selectionManager), plugin);

        SWECommand sweCmd = new SWECommand(selectionManager);
        String[] sweCommands = {"swe_pos1","swe_pos2","swe_set","swe_replace","swe_fill",
                "swe_walls","swe_outline","swe_copy","swe_paste","swe_undo","swe_rotate","swe_sel"};
        for (String cmd : sweCommands) {
            if (plugin.getCommand(cmd) != null) plugin.getCommand(cmd).setExecutor(sweCmd);
        }
    }

    public static SelectionManager getSelectionManager() { return selectionManager; }
}
