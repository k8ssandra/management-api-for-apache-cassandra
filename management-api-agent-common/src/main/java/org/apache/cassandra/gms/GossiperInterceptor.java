/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package org.apache.cassandra.gms;

import com.datastax.mgmtapi.ShimLoader;
import java.util.concurrent.Callable;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public class GossiperInterceptor {
  public static ElementMatcher<? super TypeDescription> type() {
    return ElementMatchers.nameEndsWith(".Gossiper");
  }

  public static AgentBuilder.Transformer transformer() {
    return (builder, typeDescription, classLoader, javaModule, protectionDomain) ->
        builder
            .method(ElementMatchers.named("buildSeedList"))
            .intercept(MethodDelegation.to(GossiperInterceptor.class));
  }

  public static void intercept(@SuperCall Callable<Void> zuper) throws Exception {
    ShimLoader.instance.get().reloadSeeds();
  }
}
