/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import org.junit.Test;

public class ConfigReaderTest {

  @Test
  public void readEmptyConfig() {
    System.clearProperty(ConfigReader.CONFIG_PATH_PROPERTY);

    Configuration configuration = ConfigReader.readCustomConfig();
    assertEquals(0, configuration.getRelabels().size());
    assertNull(configuration.getEndpointConfiguration());
    assertFalse(configuration.isExtendedDisabled());
  }

  @Test
  public void readFromConfigFile() {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    URL resource = classLoader.getResource("collector.yaml");

    System.setProperty(ConfigReader.CONFIG_PATH_PROPERTY, resource.getFile());
    Configuration configuration = ConfigReader.readCustomConfig();
    assertEquals(3, configuration.getRelabels().size());
    assertEquals(9000, configuration.getEndpointConfiguration().getPort());
    assertEquals("127.0.0.1", configuration.getEndpointConfiguration().getHost());

    assertNull(configuration.getEndpointConfiguration().getTlsConfig());
    assertNotNull(configuration.getLabels());
    assertNotNull(configuration.getLabels().getEnvVariables());
    assertEquals(2, configuration.getLabels().getEnvVariables().size());
    assertEquals("POD_NAME", configuration.getLabels().getEnvVariables().get("pod_name"));
  }

  @Test
  public void readFromTLSConfigFile() {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    URL resource = classLoader.getResource("collector_tls.yaml");

    System.setProperty(ConfigReader.CONFIG_PATH_PROPERTY, resource.getFile());
    Configuration configuration = ConfigReader.readCustomConfig();
    assertEquals(2, configuration.getRelabels().size());
    assertEquals(9103, configuration.getEndpointConfiguration().getPort());
    assertNull(configuration.getLabels());

    assertNotNull(configuration.getEndpointConfiguration());
    assertNotNull(configuration.getEndpointConfiguration().getTlsConfig());
    TLSConfiguration tlsConfig = configuration.getEndpointConfiguration().getTlsConfig();
    assertEquals("/etc/ssl/ca.crt", tlsConfig.getCaCertPath());
    assertEquals("/etc/ssl/tls.crt", tlsConfig.getTlsCertPath());
    assertEquals("/etc/ssl/tls.key", tlsConfig.getTlsKeyPath());

    assertTrue(configuration.isExtendedDisabled());
  }

  @Test
  public void verifyCustomRelabelRulesAreAppended() {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    URL resource = classLoader.getResource("collector_tls.yaml");

    System.setProperty(ConfigReader.CONFIG_PATH_PROPERTY, resource.getFile());
    Configuration configuration = ConfigReader.readConfig();
    assertEquals(34, configuration.getRelabels().size());
    assertEquals("(.*);(b.*)", configuration.getRelabels().get(32).getRegexp().toString());
    assertEquals("^(a|b|c),.*", configuration.getRelabels().get(33).getRegexp().toString());
  }
}
