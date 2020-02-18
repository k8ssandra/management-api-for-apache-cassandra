/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */

package com.datastax.mgmtapi.rpc;

import java.net.InetSocketAddress;

import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.service.ClientState;

/** Holds the information about client who performs a client request */
public class RpcClientState
{
    public final AuthenticatedUser user;
    public final InetSocketAddress remoteAddress;

    public RpcClientState(AuthenticatedUser user, InetSocketAddress remoteAddress)
    {
        this.user = user;
        this.remoteAddress = remoteAddress;
    }

    public static RpcClientState fromClientState(ClientState clientState)
    {
        return new RpcClientState(
                clientState.getUser(),
                clientState.getRemoteAddress());
    }
}
