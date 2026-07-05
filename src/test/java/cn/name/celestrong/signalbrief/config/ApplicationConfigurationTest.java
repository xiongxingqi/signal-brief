package cn.name.celestrong.signalbrief.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;

class ApplicationConfigurationTest {

    @Test
    void commonConfigurationDoesNotActivateProfile() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> propertySources = loader.load("application", new ClassPathResource("application.yaml"));

        Object activeProfile = propertySources.stream()
                .map(propertySource -> propertySource.getProperty("spring.profiles.active"))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);

        assertNull(activeProfile);
    }
}
