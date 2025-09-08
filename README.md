# Management API for Apache Cassandra&reg;

![Java CI](https://github.com/k8ssandra/management-api-for-apache-cassandra/workflows/Java%20CI/badge.svg)
![Docker Release](https://github.com/k8ssandra/management-api-for-apache-cassandra/workflows/Docker%20Release/badge.svg)
## Introduction

   Cassandra operations have historically been command line driven.
   The management of operational tools for Apache Cassandra have been mostly
   outsourced to teams who manage their specific environments.

   The result is a fragmented and tribal set of best practices, workarounds,
   and edge cases.

   The Management API is a sidecar service layer that attempts to build a well supported
   set of operational actions on Cassandra nodes that can be administered centrally.
   It currently works with official [Apache Cassandra](https://cassandra.apache.org) 3.11.x and 4.0
   via a drop in java agent.

   * Lifecycle Management
       * Start Node
       * Stop Node
   * Configuration Management (alpha)
       * Change YAML
       * Change jvm-opts
   * Health Checks
       * Kubernetes liveness/readiness checks
       * Consistency level checks
   * Per node actions
       * All nodetool commands

## Design Principles
  * Secure by default
  * Simple to use and extend
  * CQL Only for all C* interactions
    * Operations: Use `CALL` method for invoking via CQL
    * Observations: Rely on System Views

  The Management API has no configuration file.  Rather, it can only be configured from a
  small list of command line flags.  Communication by default can only be via **unix socket**
  or via a **http(s) endpoint** with optional TLS client auth.

  In a containerized setting the Management API represents **PID 1** and will be
  responsible for the lifecycle of Cassandra via the API.

  Communication between the Management API and Cassandra is via a local **unix socket** using
  CQL as it's only protocol.  This means, out of the box Cassandra can be started
  securely with no open ports!  Also, using CQL only means operators can
  execute operations via CQL directly if they wish.

  Each Management API is responsible for the local node only.  Coordination across nodes
  is up to the caller.  That being said, complex health checks can be added via CQL.

## Supported Image Matrix

The following versions of Cassandra and DSE are published to Docker and supported:

| Cassandra 4.0.x | Cassandra 4.1.x | Cassandra 5.0.x | DSE 6.8.x | DSE 6.9.x | HCD 1.1.x | HCD 1.2.x |
|---------------- | --------------- |---------------- |---------- |---------- | --------- | --------- |
| 4.0.0           | 4.1.0           | 5.0.1           | 6.8.25    | 6.9.0     | 1.1.0     | 1.2.0     |
| 4.0.1           | 4.1.1           | 5.0.2           | 6.8.26    | 6.9.1     |           |           |
| 4.0.3           | 4.1.2           | 5.0.3           | 6.8.28    | 6.9.2     |           |           |
| 4.0.4           | 4.1.3           | 5.0.4           | 6.8.29    | 6.9.3     |           |           |
| 4.0.5           | 4.1.4           |                 | 6.8.30    | 6.9.4     |           |           |
| 4.0.6           | 4.1.5           |                 | 6.8.31    | 6.9.5     |           |           |
| 4.0.7           | 4.1.6           |                 | 6.8.32    | 6.9.6     |           |           |
| 4.0.8           | 4.1.7           |                 | 6.8.33    | 6.9.7     |           |           |
| 4.0.9           | 4.1.8           |                 | 6.8.34    | 6.9.8     |           |           |
| 4.0.10          | 4.1.9           |                 | 6.8.35    | 6.9.9     |           |           |
| 4.0.11          | 4.1.10          |                 | 6.8.36    | 6.9.10    |           |           |
| 4.0.12          |                 |                 | 6.8.37    | 6.9.11    |           |           |
| 4.0.13          |                 |                 | 6.8.38    | 6.9.12    |           |           |
| 4.0.14          |                 |                 | 6.8.39    | 6.9.13    |           |           |
| 4.0.15          |                 |                 | 6.8.40    |           |           |           |
| 4.0.17          |                 |                 | 6.8.41    |           |           |           |
| 4.0.18          |                 |                 | 6.8.42    |           |           |           |
|                 |                 |                 | 6.8.43    |           |           |           |
|                 |                 |                 | 6.8.44    |           |           |           |
|                 |                 |                 | 6.8.46    |           |           |           |
|                 |                 |                 | 6.8.47    |           |           |           |
|                 |                 |                 | 6.8.48    |           |           |           |
|                 |                 |                 | 6.8.49    |           |           |           |
|                 |                 |                 | 6.8.50    |           |           |           |
|                 |                 |                 | 6.8.51    |           |           |           |
|                 |                 |                 | 6.8.52    |           |           |           |
|                 |                 |                 | 6.8.53    |           |           |           |
|                 |                 |                 | 6.8.54    |           |           |           |
|                 |                 |                 | 6.8.55    |           |           |           |
|                 |                 |                 | 6.8.56    |           |           |           |
|                 |                 |                 | 6.8.57    |           |           |           |
|                 |                 |                 | 6.8.58    |           |           |           |
|                 |                 |                 | 6.8.59    |           |           |           |

- Apache Cassandra images are available in `linux/amd64` or `linux/arm64` formats. The DSE images are available only in the `linux/amd64` format.
- All images (with the exception of Cassandra 5.0) are available as an Ubuntu based image or a RedHat UBI 8 based image.
Cassandra 5.0 images are only RedHat UBI8 based.
- All Cassandra 4.0.x and 4.1.x images come with JDK 11
- All Cassabdra 5.0.x images come with JDK17
- All DSE 6.8.x Ubuntu based images are available with either JDK 8 or JDK 11 (you have to pick, only  one JDK is installed in an image)
- All DSE 6.8.x RedHat UBI 8 based images come with JDK 8
- All DSE 6.9.x Ubuntu based images come with only JDK 11
- All DSE 6.9.x RedHat UBI 8 based images come with only JDK 11
- HCD images are not built within this repo. Only the Agent for HCD is maintained within this repo

### Java versions in Docker images

As of v0.1.88, all images produced from this repo will have Java 11 or newer installed as the 
Management API server code must now run with Java 11. For images where the Cassandra/DSE
version runs with Java 8 (see above), Both Java 8 and Java 11 will be available, with
Java 8 being the default and Java 8 used to run the Cassandra/DSE process.

### Cassandra 3.11.x support is now deprecated

Cassandra 3.11.x is no longer supported as of version v0.1.88. Images with Cassandra 3.11 are still available in DockerHub.
No new Management API functionality will be released for any Cassandra 3.11 versions going forward and no new Cassandra 3.11.x
patch version images will be published going forward. The table below shows the last published Cassandra 3.11.x versions:

| Cassandra 3.11.x |
| ---------------- |
| 3.11.7           |
| 3.11.8           |
| 3.11.11          |
| 3.11.12          |
| 3.11.13          |
| 3.11.14          |
| 3.11.15          |
| 3.11.16          |
| 3.11.17          |

### Docker coordinates for Cassandra OSS images

#### Ubuntu based images (OSS)

For all Ubuntu based OSS Cassandra images, the Docker coordinates are as follows:

      k8ssandra/cass-management-api:<version>

Example for Cassandra 4.0.10

      k8ssandra/cass-management-api:4.0.10

#### RedHat UBI 8 based images (OSS)

For all RedHat UBI 8 based OSS Cassandra images, the Docker coordinates are as follows:

      k8ssandra/cass-management-api:<version>-ubi8

Example for Cassandra 4.0.10

      k8ssandra/cass-management-api:4.0.10-ubi8

### Docker coordinates for DSE 6.8.x images

#### Ubuntu based images (DSE 6.8)

For all JDK 8 Ubuntu based DSE 6.8.x images, the Docker coordinates are as follows:

      datastax/dse-mgmtapi-6_8:<version>

Example for DSE 6.8.31

      datastax/dse-mgmtapi-6_8:6.8.31

For all JDK 11 Ubuntu based DSE 6.8.x images, the Docker coordinates are as follows:

      datastax/dse-mgmtapi-6_8:<version>-jdk11

Example for DSE 6.8.31

      datastax/dse-mgmtapi-6_8:6.8.31-jdk11

#### RedHat UBI 8 based images (DSE 6.8)

For all RedHat UBI 8 based DSE 6.8.x images, the Docker coordinates are as follows:

      datastax/dse-mgmtapi-6_8:<version>-ubi8

Example for DSE 6.8.31

      datastax/dse-mgmtapi-6_8:6.8.31-ubi8

### Docker coordinates for DSE 6.9.x images

#### Ubuntu based images (DSE 6.9)

For all JDK 11 Ubuntu based DSE 6.8.x images, the Docker coordinates are as follows:

      datastax/dse-mgmtapi-6_8:<version>-jdk11

Example for DSE 6.9.0

      datastax/dse-mgmtapi-6_8:6.9.0-jdk11

#### RedHat UBI 8 based images (DSE 6.9)

For all RedHat UBI 8 based DSE 6.9.x images, the Docker coordinates are as follows:

      datastax/dse-mgmtapi-6_8:<version>-ubi8

Example for DSE 6.9.0

      datastax/dse-mgmtapi-6_8:6.9.0-ubi8

** NOTE: The docker repo is not a typo, it really is `datastax/dse-mgmtapi-6_8` for 6.9 images

### Docker coordinates for HCD 1.1.x/1.2.x images

#### Ubuntu based images (HCD 1.1/1.2)

For all JDK 11 Ubuntu based HCD 1.1.x/1.2.x images, the Docker coordinates are as follows:

      datastax/hcd:<version>

Example for HCD 1.1.0

      datastax/hcd:1.1.0

Example for HCD 1.2.0

      datastax/hcd:1.2.0

#### RedHat UBI images (HCD 1.1/1.2)

For all RedHat UBI based HCD 1.1.x/1.2.x images, the Docker coordinates are as follows:

      datastax/hcd:<version>-ubi

Example for HCD 1.1.0

      datastax/hcd:1.0.0-ubi

Example for HCD 1.2.0

      datastax/hcd:1.2.0-ubi

## Building

### Minimum Java Version

The project has been updated to now require JDK11 or newer to build. The jarfile artifacts
are still compiled to Java8 as Java8 is still what some Cassandra versions ship with.

### Containers

First, you will need to have the [Docker buildx plugin](https://docs.docker.com/build/buildx/install/) installed.

To build an image based on the desired Cassandra version see the examples below:

    #Create a docker image with management api and C* 4.0 (version 4.0.0 and newer are supported)
    docker buildx build --load --build-arg CASSANDRA_VERSION=4.0.6 --tag mgmtapi-4_0 --file cassandra/Dockerfile-4.0 --target cassandra --platform linux/amd64 .

    #Create a docker image with management api and C* 4.1 (version 4.1.0 and newer are supported)
    docker buildx build --load --build-arg CASSANDRA_VERSION=4.1.4 --tag mgmtapi-4_1 --file cassandra/Dockerfile-4.1 --target cassandra --platform linux/amd64 .

    # Cassandra 5.0 and newer images are based on RedHat Universal Base Images (see below)

To build a RedHat Universal Base Image (UBI) based Cassandra image, use the `ubi8` Dockerfile. Examples:

    #Create a UBI8 based image with management api and C* 4.0 (version 4.0.0 and newer are supported)
    docker buildx build --load --build-arg CASSANDRA_VERSION=4.0.6 --tag mgmtapi-4_0_ubi8 --file cassandra/Dockerfile-4.0.ubi8 --target cassandra --platform linux/amd64 .

    #Create a UBI8 based image with management api and C* 4.1 (version 4.1.0 and newer are supported)
    docker buildx build --load --build-arg CASSANDRA_VERSION=4.1.4 --tag mgmtapi-4_1_ubi8 --file cassandra/Dockerfile-4.1.ubi8 --target cassandra --platform linux/amd64 .

    #Create a UBI8 based image with management api and C* 5.0 (version 5.0.1 and newer are supported)
    docker buildx build --load --build-arg CASSANDRA_VERSION=5.0.2 --tag mgmtapi-5_0_ubi8 --file cassandra/Dockerfile-5.0.ubi8 --target cassandra --platform linux/amd64 .

You can also build OSS Cassandra images for `linux/arm64` based platforms. Both Ubuntu and UBI8 based images support this. Simply change the `--platform` argument above to `--platform linux/arm64`. Examples:

    #Create an ARM64 UBI8 based image with management api and C* 4.0 (version 4.0.0 and newer are supported)
    docker buildx build --load --build-arg CASSANDRA_VERSION=4.0.6 --tag mgmtapi-4_0_ubi8-arm --file cassandra/Dockerfile-4.0.ubi8 --target cassandra --platform linux/arm64 .

To build an image based on DSE, see the [DSE README](management-api-agent-dse-6.8/README.md).

### Standalone

    mvn -DskipTests package
    mvn test
    mvn integration-test -Drun3.11tests=true -Drun4.0tests=true

**NOTE 1:** Running ````integration-test````s will also run unit tests.

**NOTE 2:** Running ````integration-test````s requires at least one of ````-Drun3.11tests````, ````-Drun3.11testsUBI````, ````-Drun4.0tests````, ````-Drun4.0testsUBI````, ````-Drun4.1tests````, ````-Drun4.1testsUBI````, ````-Drun5.0testsUBI````, ````-DrunDSE6.8tests````, ````-DrunDSE6.8testsUBI````, ````-DrunDSE6.9tests````, or ````-DrunDSE6.9testsUBI```` to be set to ````true```` (you can set any combination of them to ````true````).

**NOTE 3:** In order to run DSE integration tests, you must also enable the ````dse```` profile:

    mvn integration-test -P dse -DrunDSE6.8tests=true

### Cassandra trunk

For building an image based on the latest from Cassandra trunk, see this [README](management-api-agent-5.0.x/README.md).

### DSE 6.8.x/6.9.x

For building an image based on DSE 6.8, see the [DSE 6.8 README](management-api-agent-dse-6.8/README.md).

For building an image based on DSE 6.9, see the [DSE 6.9 README](management-api-agent-dse-6.9/README.md).

## REST API
   [The current Swagger/OpenAPI documentation](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/k8ssandra/management-api-for-apache-cassandra/master/management-api-server/doc/openapi.json&nocors)

   Also readable from url root: ````/openapi.json````

## Usage

  As of v0.1.24, Management API Docker images for Apache Cassandra are consolidated into a single image repository here:

  - [Management API for Apache Cassandra](https://hub.docker.com/repository/docker/k8ssandra/cass-management-api)

  For different Cassandra versions, you will need to specify the Cassandra version as an image tag. See the [supported image matrix](#supported-image-matrix) above.

  Each of the above examples will always point to the **latest** Management API version for the associated Cassandra version. If you want a specific
  Management API version, you can append the desired version to the Cassandra version tag. For example, if you want v0.1.24 of Management API for Cassandra version 3.11.9:

     docker pull k8ssandra/cass-management-api:3.11.9-v0.1.24

  For Management API versions v0.1.23 and lower, you will need to use the old Docker repositories, which are Cassandra version specific:

  - [Management API for Apache Cassandra 3.11.7](https://hub.docker.com/repository/docker/datastax/cassandra-mgmtapi-3_11_7)
  - [Management API for Apache Cassandra 3.11.8](https://hub.docker.com/repository/docker/datastax/cassandra-mgmtapi-3_11_8)
  - [Management API for Apache Cassandra 3.11.9](https://hub.docker.com/repository/docker/datastax/cassandra-mgmtapi-3_11_9)
  - [Management API for Apache Cassandra 3.11.10](https://hub.docker.com/repository/docker/datastax/cassandra-mgmtapi-3_11_10)
  - [Management API for Apache Cassandra 4.0-beta4](https://hub.docker.com/repository/docker/datastax/cassandra-mgmtapi-4_0_0).

  For DSE Docker images, see the [DSE 6.8 README](management-api-agent-dse-6.8/README.md) or
  the [DSE 6.9 README](management-api-agent-dse-6.9/README.md).

  For running standalone the jars can be downloaded from the github release:
     [Management API Releases Zip](https://github.com/k8ssandra/management-api-for-apache-cassandra/releases)

  The Management API can be run as a standalone service or along with the Kubernetes
  [cass-operator](https://github.com/datastax/cass-operator).

  The Management API is configured from the CLI. To start the service with a C* version built above, run:

     > docker run -e USE_MGMT_API=true -p 8080:8080 -it --rm mgmtapi-4_0

     > curl http://localhost:8080/api/v0/probes/liveness
     OK

     # Check service and C* are running
     > curl http://localhost:8080/api/v0/probes/readiness
     OK

### Specifying an alternate listen port

By default, all images will listen on port 8080 for Management API connections. This can be overridden by specifying
the environment variable `MGMT_API_LISTEN_TCP_PORT` and setting it to your desired port. For example:

    > docker run -e USE_MGMT_API=true -e MGMT_API_LISTEN_TCP_PORT=9090 -p 9090:9090 k8ssandra/cass-management-api:4.0.15

The above would run a Cassandra 4.0.15 image with Management API listening on port 9090 (instead of 8080).

## Usage with DSE

Please see the [DSE 6.8 README](management-api-agent-dse-6.8/README.md) or the
[DSE 6.9 README](management-api-agent-dse-6.9/README.md) for details.

## Using the Service with a locally installed C* or DSE instance


  To start the service with a locally installed C* or DSE instance, you would run the below commands. The Management API will figure out
  through `--db-home` whether it points to a C* or DSE folder

    # REQUIRED: Add management api agent to C*/DSE startup
    > export JVM_EXTRA_OPTS="-javaagent:$PWD/management-api-agent/target/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar"

    > alias mgmtapi="java -jar management-api-server/target/datastax-mgmtapi-server-0.1.0-SNAPSHOT.jar"

    # Start the service with a local unix socket only, you could also pass -H http://localhost:8080 to expose a port
    > mgmtapi --db-socket=/tmp/db.sock --host=unix:///tmp/mgmtapi.sock --db-home=<pathToCassandraOrDseHome>

    # Cassandra/DSE will be started by the service by default unless you pass --explicit-start flag

    # Check the service is up
    > curl --unix-socket /tmp/mgmtapi.sock http://localhost/api/v0/probes/liveness
    OK

    # Check C*/DSE is up
    > curl --unix-socket /tmp/mgmtapi.sock http://localhost/api/v0/probes/readiness
    OK

    # Stop C*/DSE
    curl -XPOST --unix-socket /tmp/mgmtapi.sock http://localhost/api/v0/lifecycle/stop
    OK

# Making changes

## Code Formatting

### Google Java Style

The project uses [google-java-format](https://github.com/google/google-java-format) and enforces the
[Google Java Style](https://google.github.io/styleguide/javaguide.html) for all Java source files. The
Maven plugin is configured to check the style during compile and it will fail the compile if it finds
a file that does not adhere to the coding standard.

#### Checking the format

If you want to check the formatting from the command line after making changes, you can simply run:

    mvn fmt:check

NOTE: If you are making changes in the DSE agent, you need to enable the `dse` profile:

    mvn -Pdse fmt:check

#### Formatting the code

If you want have the plugin format the code for you, you can simply run:

    mvn fmt:format

NOTE: If you are making changes in the DSE agent, you need to enable the `dse` profile:

    mvn -Pdse fmt:format

#### Using Checkstyle in an IDE

You can also install a checkstyle file in some popular IDEs to automatically format your code. The
Google checkstyle file can be found here: [google_checks.xml](checkstyle/google_checks.xml)

Refer to your IDE's documentation for installing and setting up checkstyle.

### Source code headers

In addition to Java style formatting, the project also enforces that source files have the correct
header. Source files include `.java`, `.xml` and `.properties` files. The Header should be:

    /*
     * Copyright DataStax, Inc.
     *
     * Please see the included license file for details.
     */

for Java files. For XML and Properties files, the same header should exist, with the appropriate
comment characters replacing the Java comment characters above.

Just like the Coding style, the Headers are checked at compile time and will fail the compile if
they aren't correct.

#### Checking the headers

If you want to check the headers from the command line after making changes, you can simply run:

    mvn license:check

NOTE: If you are making changes in the DSE agent, you need to enable the `dse` profile:

    mvn -Pdse license:check

#### Formatting the code

If you want have the plugin format the headers for you, you can simply run:

    mvn license:format

NOTE: If you are making changes in the DSE agent, you need to enable the `dse` profile:

    mvn -Pdse license:format

### XML formatting

The project also enforces a standard XML format. Again, it is checked at compile time and will fail
the compile if XML files are not formatted correctly. See the plugin documentation for formatting
details here: https://acegi.github.io/xml-format-maven-plugin/?utm_source=mavenlibs.com

#### Checking XML file formatting

If you want to check XML files from the command line after making changes, you can simply run:

    mvn xml-format:xml-check

NOTE: If you are making changes in the DSE agent, you need to enable the `dse` profile:

    mvn -Pdse xml-format:xml-check

#### Formatting XML files

If you want have the plugin format XML files for you, you can simply run:

    mvn xml-format:xml-format

NOTE: If you are making changes in the DSE agent, you need to enable the `dse` profile:

    mvn -Pdse xml-format:xml-format

## Design Summary

The architecture of this repository is laid as follows, front-to-back:

1. The `management-api-server/doc/openapi.json` documents the API.
2. The server implements the HTTP verbs/endpoints under the `management-api-server/src/main/java/com/datastax/mgmtapi/resources` folder (e.g. `NodeOpsresources.java`).
3. The server methods communicate back to the agents using `cqlService.executePreparedStatement()` calls which are routed as plaintext through a local socket. These calls return `ResultSet` objects, and to access scalar values within these you are best to call `.one()` before checking for nulls and `.getObject(0)`. This java object can then be serialized into JSON for return to the client.
4. The server communicates only with the `management-api-agent-common` sub-project, which holds the un-versioned `CassandraAPI` interface.
5. The `management-api-agent-common/src/main/java/com/datastax/mgmtapi/NodeOpsProvider.java` routes commands through to specific versioned instances of `CassandraAPI` which is implemented in the version 3x/4x subprojects as `CassandraAPI4x`/`CassandraAPI3x`.

Any change to add endpoints or features will need to make modifications in each of the above components to ensure that they propagate through.

## Changes to API endpoints

If you are adding a new endpoint, removing an endpoint, or otherwise changing the public API of an endpoint, you will need to re-generate the OpenAPI/Swagger document. The document lives at [management-api-server/doc/openapi.json](management-api-server/doc/openapi.json) and is regenerated during the build's `compile` phase. If your changes to code cause the API to change, you will need to perform a local `mvn compile` to regenerate the document and then add the change to your git commit.

```sh
mvn clean compile
git add management-api-server/doc/openapi.json
git commit
```

## API Client Generation

In addition to automatic OpenAPI document generation, a Golang client or a Java client can be generated during the build (unfortunately, only 
one of them can be generated at a time, but you can run the `process-classes` goal back-to-back to generate them both). The Java client generation
is enabled by default (or can be explicitly enabled with the `java-clientgen` Maven profile). The Go client generation is disabled by default
and can be enabled with the `go-clientgen` Maven profile. The clients are built using the
[OpenAPI Tools generator Maven plugin](https://github.com/OpenAPITools/openapi-generator/tree/master/modules/openapi-generator-maven-plugin)
and can be used by projects to interact with the Management API. The client generation happens during the `process-classes`
phase of the Maven build so that changes to the API implementation can be compiled into an OpenAPI document spec file
[during the compile phase](#changes-to-api-endpoints) of the build. The client code is generated in the `target` directory under
the [management-api-server](management-api-server) sub-module and should be located at

```sh
management-api-server/target/generated-sources/openapi
```

To generate the Go client, run the following from the root of the project:

```sh
mvn process-classes -P go-clientgen
```

The Go client code will be generated in `management-api-server/target/generated-sources/openapi/go-client`

To generate the Java client, run the following from the root of the project:

```sh
mvn process-classes -P java-clientgen
```

or simply:

```sh
mvn process-classes
```

The Java client code will be generated in `management-api-server/target/generated-sources/openapi/java-client`

### Maven coordinates for the Java generated client

This project also has a workflow_dispatch job that will publish the current `master` branch version of the Java
generated client to the Datastax public Maven repository. To pull in this artifact in a Maven project, you will
need to add the Datastax Artifactory repository to your Maven settings:

```xml
  <profiles>
    <profile>
      <id>datastax</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <repositories>
        <repository>
          <id>datastax-artifactory</id>
          <name>DataStax Artifactory</name>
          <releases>
            <enabled>true</enabled>
            <updatePolicy>never</updatePolicy>
            <checksumPolicy>warn</checksumPolicy>
          </releases>
          <url>https://repo.datastax.com</url>
          <layout>default</layout>
        </repository>
      </repositories>
    </profile>
  </profiles>
```

At the current time, the artifact for the Java client will have a version that contains the Git Hash of the commit it
was built from. To add the artifact to your Maven project as a dependency, you will need something like this in your pom.xml:

```xml
<project>
  <dependencies>
    <dependency>
      <groupId>io.k8ssandra</groupId>
      <artifactId>datastax-mgmtapi-client-openapi</artifactId>
      <version>0.1.0-9d71b60</version>
    </dependency>
  </dependnecies>
</project>
```

where `9d71b60` is the hash of the release you want.

Eventually, this artifact will be published into Maven Central and have a regular release version (i.e. 0.1.0).

## Published Docker images

When PRs are merged into the `master` branch, if all of the integration tests pass, the CI process will build and publish all supported Docker images with GitHub commit SHA tags. These images are not intended to be used in production. They are meant for facilitating testing with dependent projects.

The format of the Docker image tag for OSS Cassandra based images will be `<Cassandra version>-<git commit sha>`. For example, if the SHA for the commit to master is `3e99e87`, then the Cassandra 3.11.11 image tag would be `3.11.11-3e99e87`. The full docker coordinates would be `k8ssandra/cass-management-api:3.11.11-3e99e87`. Once published, these images can be used for testing in dependent projects (such as [cass-operator](https://github.com/k8ssandra/cass-operator)). Testing in dependent projects is a manual process at this time and is not automated.

## Official Release process

When the `master` branch is ready for release, all that needs to be done is to create a git `tag` and push the tag. When a git tag is pushed, a GitHub Action will kick off that builds the release versions of the Docker images and publish the to DockerHub. The release tag should be formatted as:

    v0.1.X

where `X` is incremental for each release. If the most recent release version is `v0.1.32`, then to cut the next (v0.1.33) release, do the following:

    git checkout master
    git pull
    git tag v0.1.33
    git push origin refs/tags/v0.1.33

Once the tag is pushed, the release process will start and build the Docker images as well as the Maven artifacts. The images are automatically pushed to DockerHub and the Maven artifacts are published and attached to the GitHub release.

# CLI Help
  The CLI help covers the different options:

    mgmtapi --help

    NAME
            cassandra-management-api - REST service for managing an Apache
            Cassandra or DSE node

    SYNOPSIS
            cassandra-management-api
                    [ {-C | --cassandra-home | --db-home} <db_home> ]
                    [ --explicit-start <explicit_start> ] [ {-h | --help} ]
                    {-H | --host} <listen_address>...
                    [ {-K | --no-keep-alive} <no_keep_alive> ]
                    [ {-p | --pidfile} <pidfile> ]
                    {-S | --cassandra-socket | --db-socket} <db_unix_socket_file>
                    [ --tlscacert <tls_ca_cert_file> ]
                    [ --tlscert <tls_cert_file> ] [ --tlskey <tls_key_file> ]

    OPTIONS
            -C <db_home>, --cassandra-home <db_home>, --db-home <db_home>
                Path to the Cassandra or DSE root directory, if missing will use
                $CASSANDRA_HOME/$DSE_HOME respectively

                This options value must be a path on the file system that must be
                readable, writable and executable.


            --explicit-start <explicit_start>
                When using keep-alive, setting this flag will make the management
                api wait to start Cassandra/DSE until /start is called via REST

            -h, --help
                Display help information

            -H <listen_address>, --host <listen_address>
                Daemon socket(s) to listen on. (required)

            -K <no_keep_alive>, --no-keep-alive <no_keep_alive>
                Setting this flag will stop the management api from starting or
                keeping Cassandra/DSE up automatically

            -p <pidfile>, --pidfile <pidfile>
                Create a PID file at this file path.

                This options value must be a path on the file system that must be
                readable and writable.


            -S <db_unix_socket_file>, --cassandra-socket <db_unix_socket_file>,
            --db-socket <db_unix_socket_file>
                Path to Cassandra/DSE unix socket file (required)

                This options value must be a path on the file system that must be
                readable and writable.


            --tlscacert <tls_ca_cert_file>
                Path to trust certs signed only by this CA

                This options value must be a path on the file system that must be
                readable.


            --tlscert <tls_cert_file>
                Path to TLS certificate file

                This options value must be a path on the file system that must be
                readable.


            --tlskey <tls_key_file>
                Path to TLS key file

                This options value must be a path on the file system that must be
                readable.


    COPYRIGHT
            Copyright (c) DataStax 2020

    LICENSE
            Please see https://www.apache.org/licenses/LICENSE-2.0 for more
            information


## Roadmap
  * CQL based configuration changes
  * Configuration as system table

## License

Copyright DataStax, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

## Dependencies

For information on the packaged dependencies of the Management API for Apache Cassandra&reg; and their licenses, check out our [open source report](https://app.fossa.com/reports/cec8824e-b23c-455e-b40d-8117b346affc).
