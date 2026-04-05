package io.github.md5sha256.realty.plan;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.extension.ExtensionService;
import io.github.md5sha256.realty.api.RealtyBackend;

final class PlanRegistration {

    private final RealtyDataExtension extension;

    PlanRegistration(RealtyBackend realtyApi) {
        this.extension = new RealtyDataExtension(realtyApi);
    }

    void register() {
        CapabilityService.getInstance().registerEnableListener(isPlanEnabled -> {
            if (isPlanEnabled) {
                registerExtension();
            }
        });
        registerExtension();
    }

    void unregister() {
        try {
            ExtensionService.getInstance().unregister(extension);
        } catch (IllegalStateException ignored) {
            // ExtensionService not initialized, nothing to unregister
        }
    }

    private void registerExtension() {
        if (!CapabilityService.getInstance().hasCapability("DATA_EXTENSION_VALUES")) {
            return;
        }
        ExtensionService.getInstance().register(extension);
    }
}
