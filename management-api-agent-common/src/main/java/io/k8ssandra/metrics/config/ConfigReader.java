package io.k8ssandra.metrics.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;

public class ConfigReader {
    public static final String CONFIG_PATH_PROPERTY = "collector-config-path";
    public static final String CONFIG_PATH_DEFAULT = "/configs/metric-collector.yaml";

    public static Configuration readConfig() {
        // Check env variable if there's any changes to the config path
        String configPath = System.getProperty(CONFIG_PATH_PROPERTY);
        if(configPath == null) {
            configPath = CONFIG_PATH_DEFAULT;
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        File configFile = new File(configPath);

        // FileNotFoundException should be thrown if override is used
        if(configFile.exists() || !configPath.equals(CONFIG_PATH_DEFAULT)) {
            try {
                return mapper.readValue(configFile, Configuration.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return new Configuration();
    }
}
