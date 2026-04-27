package me.bintanq.quantumcrates.hook;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.hook.impl.ItemsAdderHook;
import me.bintanq.quantumcrates.hook.impl.MMOItemsHook;
import me.bintanq.quantumcrates.hook.impl.OraxenHook;
import me.bintanq.quantumcrates.util.Logger;

/**
 * HookManager — registers all soft-dependency integrations.
 * Hooks are only activated when the target plugin is present and enabled.
 */
public class HookManager {

    private final QuantumCrates plugin;

    private MMOItemsHook   mmoItemsHook;
    private ItemsAdderHook itemsAdderHook;
    private OraxenHook     oraxenHook;

    public HookManager(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to register all hooks. Called after all managers are initialized.
     */
    public void registerAll() {
        registerHook("MMOItems", () -> {
            mmoItemsHook = new MMOItemsHook();
            return mmoItemsHook.isEnabled();
        });

        registerHook("ItemsAdder", () -> {
            itemsAdderHook = new ItemsAdderHook();
            return itemsAdderHook.isEnabled();
        });

        registerHook("Oraxen", () -> {
            oraxenHook = new OraxenHook();
            return oraxenHook.isEnabled();
        });
    }

    private void registerHook(String name, HookLoader loader) {
        if (plugin.getServer().getPluginManager().getPlugin(name) != null) {
            try {
                boolean success = loader.load();
                if (success) {
                    Logger.info("Hook &a" + name + " &fregistered successfully.");
                } else {
                    Logger.warn("Hook " + name + " plugin found but hook failed to initialize.");
                }
            } catch (Exception e) {
                Logger.warn("Failed to register hook " + name + ": " + e.getMessage());
            }
        } else {
            Logger.debug("Hook " + name + " skipped — plugin not present.");
        }
    }

    @FunctionalInterface
    private interface HookLoader {
        boolean load() throws Exception;
    }

    /* ─────────────────────── Getters ─────────────────────── */

    public MMOItemsHook   getMmoItemsHook()   { return mmoItemsHook; }
    public ItemsAdderHook getItemsAdderHook() { return itemsAdderHook; }
    public OraxenHook     getOraxenHook()     { return oraxenHook; }
}
