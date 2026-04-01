package io.github.md5sha256.realty.plan;

import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.ExtensionService;
import io.github.md5sha256.realty.api.RealtyApi;
import org.bukkit.plugin.java.JavaPlugin;

public final class RealtyPaperPlanExtensionPlugin extends JavaPlugin {

    private DataExtension dataExtension;

    @Override
    public void onEnable() {
        var provider = getServer().getServicesManager().getRegistration(RealtyApi.class);
        if (provider == null) {
            getLogger().severe("Missing realty api");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        var api = provider.getProvider();
        this.dataExtension = new RealtyDataExtension(api);
        ExtensionService.getInstance().register(dataExtension);
    }

    @Override
    public void onDisable() {
        ExtensionService.getInstance().unregister(dataExtension);
    }
}
