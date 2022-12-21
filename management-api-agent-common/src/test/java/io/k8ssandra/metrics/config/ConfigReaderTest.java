package io.k8ssandra.metrics.config;

import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.assertEquals;

public class ConfigReaderTest {

    @Test
    public void readEmptyConfig() {
        Configuration configuration = ConfigReader.readConfig();
        assertEquals(0, configuration.getFilters().size());
    }

    @Test
    public void readFromConfigFile() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource("collector.yaml");

        System.setProperty(ConfigReader.CONFIG_PATH_PROPERTY, resource.getFile());
        Configuration configuration = ConfigReader.readConfig();
        assertEquals(2, configuration.getFilters().size());
        assertEquals(9001, configuration.getPort());
    }
}
