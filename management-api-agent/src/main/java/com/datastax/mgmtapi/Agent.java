package com.datastax.mgmtapi;

import com.datastax.mgmtapi.interceptors.CassandraDaemonInterceptor;
import com.datastax.mgmtapi.interceptors.QueryHandlerInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

public class Agent {

    public static void premain(String arg, Instrumentation inst) throws Exception {

        File temp = Files.createTempDirectory("tmp").toFile();
        temp.deleteOnExit();

        Map<TypeDescription, byte[]> injected = new HashMap<>();

        injected.put(new TypeDescription.ForLoadedType(CassandraDaemonInterceptor.class), ClassFileLocator.ForClassLoader.read(CassandraDaemonInterceptor.class));
        ClassInjector.UsingInstrumentation.of(temp, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, inst).inject(injected);

        new AgentBuilder.Default()
                //.disableClassFormatChanges()
                //.with(AgentBuilder.Listener.StreamWriting.toSystemOut().withTransformationsOnly()) //For debug
                .ignore(new AgentBuilder.RawMatcher.ForElementMatchers(nameStartsWith("net.bytebuddy.").or(isSynthetic()), any(), any()))
                //.enableBootstrapInjection(inst, temp)
                //Cassandra Daemon
                .type(CassandraDaemonInterceptor.type())
                .transform(CassandraDaemonInterceptor.transformer())
                //Query Handler
                .type(QueryHandlerInterceptor.type())
                .transform(QueryHandlerInterceptor.transformer())
                .installOn(inst);
    }
}