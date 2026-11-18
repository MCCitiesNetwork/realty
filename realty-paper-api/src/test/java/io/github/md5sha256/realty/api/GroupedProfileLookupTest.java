package io.github.md5sha256.realty.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Whether a region name is listed in a grouped profile, which a rename would leave behind. */
class GroupedProfileLookupTest {

    private final RegionProfileService service = new RegionProfileService(Logger.getLogger("test"));

    @Test
    void aNameInAGroupedFlagProfileIsReported() {
        this.service.addGroupedFlagProfile(Set.of("plot-a"), Map.of());

        Assertions.assertTrue(this.service.hasGroupedProfile("plot-a"));
        Assertions.assertFalse(this.service.hasGroupedProfile("plot-b"));
    }

    @Test
    void aNameInAGroupedSignProfileIsReported() {
        this.service.addGroupedSignProfile(Set.of("plot-a"), Map.of());

        Assertions.assertTrue(this.service.hasGroupedProfile("plot-a"));
        Assertions.assertFalse(this.service.hasGroupedProfile("plot-b"));
    }

    @Test
    void noGroupedProfileMeansNoNameIsReported() {
        Assertions.assertFalse(this.service.hasGroupedProfile("plot-a"));
    }
}
