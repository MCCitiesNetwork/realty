package io.github.md5sha256.realty.plan;

import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.extractor.ExtensionExtractor;
import io.github.md5sha256.realty.api.RealtyBackend;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RealtyDataExtensionTest {

    @Test
    void noImplementationErrors() {
        RealtyBackend stubApi = (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                (proxy, method, args) -> null
        );
        DataExtension extension = new RealtyDataExtension(stubApi);
        assertDoesNotThrow(() -> new ExtensionExtractor(extension).validateAnnotations());
    }
}
