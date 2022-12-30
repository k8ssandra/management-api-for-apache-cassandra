package io.k8ssandra.metrics.config;

import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.*;

public class ConfigReaderTest {

    @Test
    public void readEmptyConfig() {
        System.clearProperty(ConfigReader.CONFIG_PATH_PROPERTY);

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

        assertNull(configuration.getTlsConfig());
    }

    @Test
    public void readFromTLSConfigFile() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource("collector_tls.yaml");

        System.setProperty(ConfigReader.CONFIG_PATH_PROPERTY, resource.getFile());
        Configuration configuration = ConfigReader.readConfig();
        assertEquals(2, configuration.getFilters().size());
        assertEquals(9001, configuration.getPort());

        assertNotNull(configuration.getTlsConfig());
        TLSConfiguration tlsConfig = configuration.getTlsConfig();
        assertEquals("/etc/ssl/ca.crt", tlsConfig.getCaCertPath());
        assertEquals("/etc/ssl/tls.crt", tlsConfig.getTlsCertPath());
        assertEquals("/etc/ssl/tls.key", tlsConfig.getTlsKeyPath());
    }
}
