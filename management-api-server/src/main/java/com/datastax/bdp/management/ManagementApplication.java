/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.bdp.management;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import com.datastax.bdp.management.resources.AuthResources;
import com.datastax.bdp.management.resources.KeyspaceOpsResources;
import com.datastax.bdp.management.resources.MetadataResources;
import com.datastax.bdp.management.resources.NodeOpsResources;
import com.datastax.bdp.management.resources.TableOpsResources;
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.bdp.management.resources.K8OperatorResources;
import com.datastax.bdp.management.resources.LifecycleResources;
import io.swagger.v3.jaxrs2.SwaggerSerializers;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;


@ApplicationPath("/")
public class ManagementApplication extends Application
{
    private static final Logger logger = LoggerFactory.getLogger(ManagementApplication.class);

    public final File dseUnixSocketFile;
    public final File cassandraHome;
    public final Collection<String> dseExtraArgs;
    public final CqlService cqlService;


    private final LifecycleResources lifecycle;
    private final Set<Object> resources;
    private final AtomicReference<STATE> requestedState = new AtomicReference<>(STATE.UNKNOWN);
    private final AtomicReference<String> activeProfile = new AtomicReference<>(null);

    public ManagementApplication(File cassandraHome, File dseUnixSocketFile, CqlService cqlService, Collection<String> dseExtraArgs)
    {
        this.dseUnixSocketFile = dseUnixSocketFile;
        this.cassandraHome = cassandraHome;
        this.dseExtraArgs = dseExtraArgs;
        this.lifecycle = new LifecycleResources(this);
        this.cqlService = cqlService;

        resources = ImmutableSet.of(
                lifecycle,
                new K8OperatorResources(this),
                new KeyspaceOpsResources(this),
                new MetadataResources(this),
                new NodeOpsResources(this),
                new TableOpsResources(this),
                new AuthResources(this),
                new OpenApiResource(),
                new SwaggerSerializers()
        );
    }

    @Override
    public Set<Object> getSingletons()
    {
        return resources;
    }

    public boolean checkState()
    {
        try
        {
            STATE currentState = getRequestedState();
            logger.debug("Current Requested State is {}", currentState);
            if (currentState != STATE.STOPPED)
            {
                Response r = lifecycle.startNode(getActiveProfile());
                return r.getStatus() >= 200 && r.getStatus() < 300;
            }

            return true;
        }
        catch (Throwable t)
        {
            logger.error("Error checking state", t);
            return false;
        }
    }

    public STATE getRequestedState()
    {
        return requestedState.get();
    }

    public void setRequestedState(STATE state)
    {
        requestedState.set(state);
    }

    public String getActiveProfile()
    {
        return activeProfile.get();
    }

    public void setActiveProfile(String profile)
    {
        activeProfile.set(profile);
    }

    public enum STATE {
        UNKNOWN,
        STARTED,
        STOPPED
    }
}
