/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.util;

import java.util.MissingResourceException;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceProviderLoader<SP>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceProviderLoader.class);

    public SP getProvider(Class<SP> clazz)
    {
        ServiceLoader<SP> loader = ServiceLoader.load(clazz);
        SP tempProvider = null;
        for (SP provider : loader)
        {
            if (tempProvider == null)
            {
                tempProvider = provider;
            }
            else
            {
                LOGGER.error(
                        String.format(
                                "ServiceProvider for %s already found. Extra provider: %s",
                                clazz.getName(),
                                provider.getClass().getName()));
            }
        }
        if (tempProvider == null)
        {
            throw new MissingResourceException(
                    "No ServiceProvider found. Please check that an agent jarfile is deployed.",
                    clazz.getName(),
                    "SPI configuration");
        }
        return tempProvider;
    }
}
