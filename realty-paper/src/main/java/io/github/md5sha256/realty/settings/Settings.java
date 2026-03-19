package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.UUID;

@ConfigSerializable
public record Settings(
        @Setting("default-sale-authority-uuid") @Required @NotNull UUID defaultSaleAuthority,
        @Setting("default-sale-titleholder-uuid")@Nullable UUID defaultSaleTitleholder,
        @Setting("default-lease-authority-uuid") @Required @NotNull UUID defaultLeaseAuthority
) {
}
