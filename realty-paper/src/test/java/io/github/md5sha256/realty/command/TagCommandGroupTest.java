package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.RegionTagMapper;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.RealtyTags;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** A tag goes only on a region that is registered with Realty. */
class TagCommandGroupTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final MessageContainer MESSAGES = shippedMessages();

    private final Database database = Mockito.mock(Database.class);
    private final SqlSessionWrapper session = Mockito.mock(SqlSessionWrapper.class);
    private final RealtyRegionMapper regions = Mockito.mock(RealtyRegionMapper.class);
    private final RegionTagMapper tags = Mockito.mock(RegionTagMapper.class);
    private final CommandSender sender = Mockito.mock(CommandSender.class);
    private TagCommandGroup command;

    @BeforeEach
    void setUp() {
        Mockito.when(this.database.openSession(true)).thenReturn(this.session);
        Mockito.when(this.session.realtyRegionMapper()).thenReturn(this.regions);
        Mockito.when(this.session.regionTagMapper()).thenReturn(this.tags);
        this.command = new TagCommandGroup(this.database, Mockito.mock(ExecutorState.class),
                new AtomicReference<>(Mockito.mock(RealtyTags.class)), MESSAGES);
    }

    @Test
    void aRegionThatIsNotRegisteredIsRefused() {
        Mockito.when(this.regions.selectByWorldGuardRegion("spawn", WORLD_ID)).thenReturn(null);

        this.command.addTag(this.sender, "commercial", "spawn", WORLD_ID);

        Mockito.verify(this.sender).sendMessage(MESSAGES.messageFor(MessageKeys.TAG_ADD_NOT_REGISTERED,
                Placeholder.unparsed("region", "spawn")));
        Mockito.verify(this.tags, Mockito.never()).insert(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void aRegisteredRegionIsTagged() {
        Mockito.when(this.regions.selectByWorldGuardRegion("plot-12", WORLD_ID))
                .thenReturn(new RealtyRegionEntity(1, "plot-12", WORLD_ID));
        Mockito.when(this.tags.insert("commercial", "plot-12", WORLD_ID)).thenReturn(1);

        this.command.addTag(this.sender, "commercial", "plot-12", WORLD_ID);

        Mockito.verify(this.sender).sendMessage(MESSAGES.messageFor(MessageKeys.TAG_ADD_SUCCESS,
                Placeholder.unparsed("tag", "commercial"),
                Placeholder.unparsed("region", "plot-12")));
    }

    @Test
    void theRefusalIsInTheShippedMessages() {
        String text = PlainTextComponentSerializer.plainText().serialize(
                MESSAGES.messageFor(MessageKeys.TAG_ADD_NOT_REGISTERED));

        Assertions.assertFalse(text.isBlank());
        Assertions.assertFalse(text.contains(MessageKeys.TAG_ADD_NOT_REGISTERED), "missing from messages.yml");
    }

    private static MessageContainer shippedMessages() {
        MessageContainer messages = new MessageContainer();
        try (InputStream in = TagCommandGroupTest.class.getResourceAsStream("/messages.yml")) {
            Assertions.assertNotNull(in);
            messages.load(YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build()
                    .load());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return messages;
    }
}
