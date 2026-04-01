package io.github.md5sha256.realty.plan;

import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.extractor.ExtensionExtractor;
import io.github.md5sha256.realty.api.RealtyApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RealtyDataExtensionTest {

    @Test
    void noImplementationErrors() {
        RealtyApi stubApi = (RealtyApi) Proxy.newProxyInstance(
                RealtyApi.class.getClassLoader(),
                new Class<?>[]{RealtyApi.class},
                (proxy, method, args) -> null
        );
        DataExtension extension = new RealtyDataExtension(stubApi);
        assertDoesNotThrow(() -> new ExtensionExtractor(extension).validateAnnotations());
    }
}
