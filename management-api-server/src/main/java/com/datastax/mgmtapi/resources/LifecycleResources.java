/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import static com.datastax.mgmtapi.ManagementApplication.STATE.STARTED;
import static com.datastax.mgmtapi.ManagementApplication.STATE.STOPPED;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.UnixCmds;
import com.datastax.mgmtapi.UnixSocketCQLAccess;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.util.ShellUtils;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.shaded.guava.common.collect.Streams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Uninterruptibles;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/v0/lifecycle")
public class LifecycleResources extends BaseResources {
  private static final Logger logger = LoggerFactory.getLogger(LifecycleResources.class);

  static final YAMLMapper yamlMapper = new YAMLMapper();
  static final String IPV4_PATTERN =
      "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$";
  private ObjectMapper objectMapper = new ObjectMapper();

  public LifecycleResources(ManagementApplication app) {
    super(app);
  }

  /**
   * Starts a Cassandra/DSE node
   *
   * <p>Handles the following: - Cassandra pid found and can connect: status 202 - Cassandra pid not
   * found and can not connect: status 201 - Cassandra pid not found but can connect: status 206 -
   * Cassandra pid found but can not connect: status 204
   *
   * @return
   */
  @Path("/start")
  @POST
  @Operation(description = "Starts Cassandra/DSE", operationId = "startNode")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "201",
      description = "Cassandra started successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(responseCode = "202", description = "Cassandra already running and can connect")
  @ApiResponse(responseCode = "204", description = "Cassandra already running but can't connect")
  @ApiResponse(responseCode = "206", description = "Cassandra process not found but can connect")
  @ApiResponse(
      responseCode = "420",
      description = "Cassandra could not start successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "Error starting Cassandra")))
  @ApiResponse(
      responseCode = "500",
      description = "Error trying to start Cassandra",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "error message")))
  public synchronized Response startNode(
      @QueryParam("profile") String profile, @QueryParam("replace_ip") String replaceIp) {
    app.setRequestedState(STARTED);

    // Todo we should add a CALL getPid command and compare;
    boolean canConnect;
    try {
      CqlSession session = UnixSocketCQLAccess.get(app.dbUnixSocketFile).get();
      session.execute("SELECT * FROM system.peers");

      canConnect = true;
    } catch (Throwable t) {
      logger.debug("Could not connect:", t);
      canConnect = false;
    }

    try {
      Optional<Integer> maybePid = findPid();

      if (maybePid.isPresent()) {
        return Response.status(canConnect ? HttpStatus.SC_ACCEPTED : HttpStatus.SC_NO_CONTENT)
            .build();
      } else if (canConnect) return Response.status(HttpStatus.SC_PARTIAL_CONTENT).build();
    } catch (Throwable t) {
      logger.error("Error checking pid", t);
      return Response.serverError().entity(Entity.text(t.getLocalizedMessage())).build();
    }

    try {
      String extra = System.getenv("JVM_EXTRA_OPTS");
      ImmutableMap.Builder<String, String> envArgsBuilder = ImmutableMap.builder();

      if (extra != null) envArgsBuilder.put("JVM_EXTRA_OPTS", extra);

      Map<String, String> environment = envArgsBuilder.build();
      List<String> cmdArgs = new ArrayList<>(app.dbExtraJvmArgs);

      if (profile != null) {
        app.setActiveProfile(profile);

        cmdArgs.add(String.format("-Dcassandra.config=file:///tmp/%s/cassandra.yaml", profile));
        cmdArgs.add(
            String.format(
                "-Dcassandra-rackdc.properties=file:///tmp/%s/node-topology.properties", profile));
      }

      if (replaceIp != null) {
        if (!replaceIp.matches(IPV4_PATTERN))
          return Response.serverError()
              .entity(Entity.text("Invalid replace IP passed: " + replaceIp))
              .build();

        cmdArgs.add(String.format("-Dcassandra.replace_address_first_boot=%s", replaceIp));
      }

      // Delete stale file if it exists
      if (app.dbUnixSocketFile.exists()) FileUtils.deleteQuietly(app.dbUnixSocketFile);

      // ensure Cassandra will be able to write to the log directory, or the process won't start
      String mgmtApiStartupLogDir = System.getenv("MGMT_API_LOG_DIR");
      if (mgmtApiStartupLogDir == null) {
        // use default
        logger.debug("MGMT_API_LOG_DIR is not set, using default of /var/log/cassandra");
        mgmtApiStartupLogDir = "/var/log/cassandra";
      }

      boolean started = false;

      if (!verifyOrCreateLogDirectory(mgmtApiStartupLogDir)) {
        logger.error(
            String.format(
                "Process cannot write to %s. Please ensure that permissions are set correctly and that the MGMT_API_LOG_DIR environment variable is set to the desired log directory.",
                mgmtApiStartupLogDir));
      } else {
        // build the start command. Add stdout/stderr redirects first
        ProcessBuilder dbCmdPb =
            new ProcessBuilder("nohup")
                .redirectError(Paths.get(mgmtApiStartupLogDir, "stderr.log").toFile())
                .redirectOutput(Paths.get(mgmtApiStartupLogDir, "stdout.log").toFile());
        // setup profile if specified
        if (profile != null) {
          dbCmdPb.command().add(String.format("/tmp/%s/env.sh", profile));
        }
        dbCmdPb.command().add(app.dbExe.getAbsolutePath());
        if (app.dbExe.getAbsolutePath().endsWith("dse")) {
          // DSE needs the extra "cassandra" startup argument
          dbCmdPb.command().add("cassandra");
        }
        dbCmdPb.command().add("-R");
        dbCmdPb.command().add("-Dcassandra.server_process");
        dbCmdPb.command().add("-Dcassandra.skip_default_role_setup=true");
        dbCmdPb
            .command()
            .add(String.format("-Ddb.unix_socket_file=%s", app.dbUnixSocketFile.getAbsolutePath()));
        // add extra commands with some sanitizing so that we do not end up with empty args or
        // surrounded by whitespace chars
        cmdArgs.stream()
            .map(this::sanitizeDbCmdArg)
            .filter(StringUtils::isNotBlank)
            .forEach(x -> dbCmdPb.command().add(x));

        started =
            ShellUtils.executeWithHandlers(
                dbCmdPb,
                (input, out) -> true,
                (exitCode, err) -> {
                  String lines = err.collect(Collectors.joining("\n"));
                  logger.error("Error starting Cassandra: {}", lines);
                  return false;
                },
                environment);
      }

      if (started) logger.info("Started Cassandra");
      else logger.warn("Error starting Cassandra");

      return Response.status(started ? HttpStatus.SC_CREATED : HttpStatus.SC_METHOD_FAILURE)
          .entity(started ? "OK\n" : "Error starting Cassandra")
          .build();
    } catch (Throwable t) {
      return Response.serverError().entity(Entity.text(t.getLocalizedMessage())).build();
    }
  }

  @Path("/stop")
  @POST
  @Operation(
      description =
          "Stops Cassandra/DSE. Keeps node from restarting automatically until /start is called",
      operationId = "stopNode")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra stopped successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "500",
      description = "Cassandra not stopped successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "Killing Cassandra Failed")))
  public synchronized Response stopNode() {
    app.setRequestedState(STOPPED);

    try {
      // 30 Seconds
      int tries = 6;
      int sleepSeconds = 5;

      do {
        Optional<Integer> maybePid = findPid();

        if (!maybePid.isPresent()) {
          logger.info("Cassandra already stopped");
          return Response.ok("OK\n").build();
        }

        Boolean stopped = UnixCmds.terminateProcess(maybePid.get());

        if (!stopped) logger.warn("Killing Cassandra failed");

        Uninterruptibles.sleepUninterruptibly(sleepSeconds, TimeUnit.SECONDS);
      } while (tries-- > 0);

      Optional<Integer> maybePid = findPid();
      if (maybePid.isPresent()) {
        Boolean stopped = UnixCmds.killProcess(maybePid.get());

        if (!stopped) {
          logger.info("Cassandra not stopped trying with kill -9");
          return Response.serverError().entity(Entity.text("Killing Cassandra Failed")).build();
        }

        Uninterruptibles.sleepUninterruptibly(sleepSeconds, TimeUnit.SECONDS);

        maybePid = findPid();
        if (maybePid.isPresent()) {
          logger.info("Cassandra is not able to die");
          return Response.serverError().entity(Entity.text("Killing Cassandra Failed")).build();
        }
      }

      return Response.ok("OK\n").build();
    } catch (Throwable t) {
      logger.error("Error when calling stop", t);
      return Response.serverError().entity(Entity.text(t.getLocalizedMessage())).build();
    }
  }

  private JsonNode findCassandraYaml() throws IOException {
    File cassandraConfig = null;

    if (System.getenv("CASSANDRA_CONF") != null) {
      cassandraConfig = new File(System.getenv("CASSANDRA_CONF") + "/cassandra.yaml");
    } else {
      cassandraConfig = new File(app.dbHome, "conf/cassandra.yaml");
    }

    if (cassandraConfig.exists() && cassandraConfig.isFile())
      return yamlMapper.readTree(cassandraConfig);

    throw new IOException("cassandra.yaml not found");
  }

  @Path("/configure")
  /**
   * Hiding both "configure" endpoints to prevent OpenAPI spec generation from happening. Having 2
   * methods handling the same endpoint and only differing on the @Consumes request MIME type causes
   * the generation to fail to document the endpoint correctly. For now, we will annotate both these
   * methods with @Hidden so that they still exist but are not auto generated in the spec file. They
   * are documented by hand in the openapi-configuration.json file in the resources directory of
   * this project for now, as that file is merged with the auto-generated spec to produce the final
   * spec file.
   */
  @Hidden
  @POST
  @Consumes("application/json")
  @Operation(
      description = "Configure Cassandra. Will fail if Cassandra is already started",
      operationId = "configureNodeJson")
  @Produces(MediaType.TEXT_PLAIN)
  public synchronized Response configureNodeJson(
      @QueryParam("profile") String profile, String config) {
    try {
      JsonNode jn = objectMapper.readTree(config);
      String yaml = yamlMapper.writeValueAsString(jn);
      return configureNode(profile, yaml);
    } catch (IOException e) {
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity("Invalid JSON:" + e.getMessage())
          .build();
    }
  }

  @Path("/configure")
  // Hiding this. See above.
  @Hidden
  @POST
  @Consumes({"application/yaml", "text/yaml"})
  @Operation(
      description = "Configure Cassandra/DSE. Will fail if Cassandra/DSE is already started",
      operationId = "configureNode")
  @Produces(MediaType.TEXT_PLAIN)
  public synchronized Response configureNode(@QueryParam("profile") String profile, String yaml) {
    if (app.getRequestedState() == STARTED) {
      return Response.status(Response.Status.NOT_ACCEPTABLE)
          .entity("Cassandra is running, try /api/v0/lifecycle/stop first\n")
          .build();
    }

    // FIXME: Regex
    if (profile == null)
      return Response.status(Response.Status.BAD_REQUEST.getStatusCode())
          .entity(
              "The profile query parameter is required, try /api/v0/lifecycle/configure?profile=<profile>\n")
          .build();

    // Find cassandra yaml
    try {
      JsonNode jn = yamlMapper.readTree(yaml.getBytes());

      jn = jn.get("spec");
      if (jn == null) {
        return Response.status(Response.Status.BAD_REQUEST).entity("spec missing\n").build();
      }

      JsonNode clusterNameJsonNode = jn.get("clusterName");
      if (clusterNameJsonNode == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("cluster name missing\n")
            .build();
      }
      String clusterName = clusterNameJsonNode.textValue();

      jn = jn.get("config");
      if (jn == null)
        return Response.status(Response.Status.BAD_REQUEST.getStatusCode())
            .entity("config missing\n")
            .build();

      JsonNode nodeTopology = jn.get("node-topology");
      String dc = "vdc1";
      String rack = "vrack1";
      if (nodeTopology != null) {
        if (nodeTopology.has("dc")) dc = nodeTopology.get("dc").asText(dc);

        if (nodeTopology.has("rack")) rack = nodeTopology.get("rack").asText(rack);
      }

      new File("/tmp/" + profile).mkdirs();

      StringBuilder sb =
          new StringBuilder()
              .append("dc=")
              .append(dc)
              .append("\n")
              .append("rack=")
              .append(rack)
              .append("\n");

      FileUtils.write(
          new File("/tmp/" + profile + "/node-topology.properties"), sb.toString(), false);

      JsonNode defaultCYaml = findCassandraYaml();
      JsonNode cassandraYaml = jn.get("cassandra-yaml");

      ObjectNode customYaml = defaultCYaml.deepCopy();

      Iterator<Map.Entry<String, JsonNode>> it = cassandraYaml.fields();
      while (it.hasNext()) {
        Map.Entry<String, JsonNode> e = it.next();
        customYaml.set(e.getKey(), e.getValue());
      }

      if (clusterName != null) customYaml.set("cluster_name", TextNode.valueOf(clusterName));

      yamlMapper.writeValue(new File("/tmp/" + profile + "/cassandra.yaml"), customYaml);

      String jvmMaxHeap = null;
      String jvmHeapNew = null;
      String jvmExtraOpts = null;
      JsonNode jvmOpts = jn.get("jvm-server-options");
      if (jvmOpts != null) {
        if (jvmOpts.has("max_heap_size")) jvmMaxHeap = jvmOpts.get("max_heap_size").asText();

        if (jvmOpts.has("heap_new_size")) jvmHeapNew = jvmOpts.get("heap_new_size").asText();

        if ((jvmMaxHeap == null) != (jvmHeapNew == null))
          return Response.status(Response.Status.BAD_REQUEST)
              .entity("Both max_heap_size and heap_new_size must be defined")
              .build();

        if (jvmOpts.has("additional-jvm-opts"))
          jvmExtraOpts =
              Streams.stream(jvmOpts.withArray("additional-jvm-opts").elements())
                  .map(JsonNode::asText)
                  .collect(Collectors.joining(" "));
      }

      sb = new StringBuilder();
      sb.append("#!/bin/sh\n");

      if (jvmMaxHeap != null)
        sb.append("export MAX_HEAP_SIZE=")
            .append(jvmMaxHeap)
            .append("\n")
            .append("export HEAP_NEWSIZE=")
            .append(jvmHeapNew)
            .append("\n");

      if (jvmExtraOpts != null)
        sb.append("export JVM_EXTRA_OPTS=\"$JVM_EXTRA_OPTS ").append(jvmExtraOpts).append("\"\n");

      sb.append("exec $*\n");

      File envScript = new File("/tmp/" + profile + "/env.sh");
      FileUtils.write(envScript, sb.toString(), false);

      envScript.setExecutable(true);
    } catch (IOException e) {
      e.printStackTrace();
      return Response.serverError().build();
    }

    return Response.ok().entity("OK\n").build();
  }

  @Path("/pid")
  @GET
  @Operation(description = "The PID of Cassandra/DSE, if it's running", operationId = "getPID")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra Process ID",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(responseCode = "204", description = "No Cassandra Process running")
  @ApiResponse(
      responseCode = "500",
      description = "Error finding Cassandra Process",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "error message")))
  public Response getPID() {
    try {
      Optional<Integer> maybePid = findPid();
      if (maybePid.isPresent()) return Response.ok(Integer.toString(maybePid.get())).build();

      return Response.status(HttpStatus.SC_NO_CONTENT).build();
    } catch (Throwable t) {
      logger.error("Error when getting pid", t);
      return Response.serverError().entity(t.getLocalizedMessage()).build();
    }
  }

  private Optional<Integer> findPid() throws IOException {
    return UnixCmds.findDbProcessWithMatchingArg(
        "-Ddb.unix_socket_file=" + app.dbUnixSocketFile.getAbsolutePath());
  }

  /**
   * Verifies that the provided log directory can be written. Will attempt to create the directory
   * if it does not exist.
   *
   * @param logDir The directory to verify write permissions
   * @return true if the directory can be written to or created, false otherwise.
   */
  private boolean verifyOrCreateLogDirectory(String logDir) {
    File logPath = new File(logDir);
    if (logPath.exists()) {
      return logPath.isDirectory() && logPath.canWrite();
    } else {
      return logPath.mkdirs();
    }
  }

  private String sanitizeDbCmdArg(String arg) {
    return StringUtils.strip(StringUtils.trim(arg));
  }
}
