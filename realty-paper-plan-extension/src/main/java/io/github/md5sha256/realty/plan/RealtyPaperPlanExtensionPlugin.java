package io.github.md5sha256.realty.plan;

import io.github.md5sha256.realty.api.RealtyApi;
import org.bukkit.plugin.java.JavaPlugin;

public final class RealtyPaperPlanExtensionPlugin extends JavaPlugin {

    private PlanRegistration registration;

    @Override
    public void onEnable() {
        var provider = getServer().getServicesManager().getRegistration(RealtyApi.class);
        if (provider == null) {
            getLogger().severe("Missing realty api");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.registration = new PlanRegistration(provider.getProvider());
        this.registration.register();
    }

    @Override
    public void onDisable() {
        if (registration != null) {
            registration.unregister();
        }
    }
}
