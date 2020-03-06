package org.apache.cassandra.gms;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.LoggerFactory;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.JVMStabilityInspector;

public class GossiperInterceptor
{
    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith(".Gossiper");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer()
        {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("buildSeedList")).intercept(MethodDelegation.to(GossiperInterceptor.class));
            }
        };
    }

    public static void intercept(@SuperCall Callable<Void> zuper) throws Exception
    {
        reloadSeeds();
    }

    public static Set<InetAddress> reloadSeeds()
    {
        // Get the new set in the same that buildSeedsList does
        Set<InetAddress> tmp = new HashSet<>();
        try
        {
            for (InetAddress seed : DatabaseDescriptor.getSeeds())
            {
                if (seed.equals(FBUtilities.getBroadcastAddress()))
                    continue;
                tmp.add(seed);
            }
        }
        // If using the SimpleSeedProvider invalid yaml added to the config since startup could
        // cause this to throw. Additionally, third party seed providers may throw exceptions.
        // Handle the error and return a null to indicate that there was a problem.
        catch (Throwable e)
        {
            JVMStabilityInspector.inspectThrowable(e);
            return null;
        }

        if (tmp.size() == 0)
        {
            return Gossiper.instance.seeds;
        }

        if (tmp.equals(Gossiper.instance.seeds))
        {
            return Gossiper.instance.seeds;
        }

        // Add the new entries
        Gossiper.instance.seeds.addAll(tmp);
        // Remove the old entries
        Gossiper.instance.seeds.retainAll(tmp);
        LoggerFactory.getLogger(GossiperInterceptor.class).debug("New seed node list after reload {}", Gossiper.instance.seeds);

        return Gossiper.instance.seeds;
    }

}
