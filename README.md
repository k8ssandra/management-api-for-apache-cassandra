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

## Building

### Minimum Java Version

The project has been updated to now require JDK11 or newer to build. The jarfile artifacts
are still compiled to Java8 as Java8 is still what some Cassandra versiosn ship with.

### Supported Image Matrix

The following versions of Cassandra and DSE are published to Docker and supported:

Cassandra 3.11.x

      k8ssandra/cass-management-api:3.11.7
      k8ssandra/cass-management-api:3.11.8
      k8ssandra/cass-management-api:3.11.11
      k8ssandra/cass-management-api:3.11.12
      k8ssandra/cass-management-api:3.11.13
      k8ssandra/cass-management-api:3.11.14

Cassandra 4.0.x

      k8ssandra/cass-management-api:4.0.0
      k8ssandra/cass-management-api:4.0.1
      k8ssandra/cass-management-api:4.0.3
      k8ssandra/cass-management-api:4.0.4
      k8ssandra/cass-management-api:4.0.5
      k8ssandra/cass-management-api:4.0.6
      k8ssandra/cass-management-api:4.0.7
      k8ssandra/cass-management-api:4.0.8
      k8ssandra/cass-management-api:4.0.9

Cassandra 4.1.x

      k8ssandra/cass-management-api:4.1.0
      k8ssandra/cass-management-api:4.1.1

Cassandra trunk

      k8ssandra/cass-management-api:4.2.0

DSE 6.8.x

      datastax/dse-mgmtapi-6_8:6.8.25 (jdk8, jdk11 and ubi7 based images)
      datastax/dse-mgmtapi-6_8:6.8.26 (jdk8, jdk11 and ubi7 based images)
      datastax/dse-mgmtapi-6_8:6.8.28 (jdk8, jdk11 and ubi7 based images)
      datastax/dse-mgmtapi-6_8:6.8.29 (jdk8, jdk11 and ubi7 based images)
      datastax/dse-mgmtapi-6_8:6.8.30 (jdk8, jdk11 and ubi7 based images)
      datastax/dse-mgmtapi-6_8:6.8.31 (jdk8, jdk11 and ubi7 based images)
      datastax/dse-mgmtapi-6_8:6.8.32 (jdk8, jdk11 and ubi7 based images)
      datastax/dse-mgmtapi-6_8:6.8.33 (jdk8, jdk11 and ubi7 based images)
      datastax/dse-mgmtapi-6_8:6.8.34 (jdk8, jdk11 and ubi7 based images)

### Cassandra trunk

For building an image based on the latest from Cassandra trunk, see this [README](management-api-agent-4.2.x/README.md)

### Containers

First, you will need to have the [Docker buildx plugin](https://docs.docker.com/build/buildx/install/) installed.

To build an image based on the desired Cassandra version see the examples below:

    #Create a docker image with management api and C* 3.11 (version 3.11.7 and newer are supported, replace `3.11.11` with the version you want below)
    docker buildx build --load --build-arg CASSANDRA_VERSION=3.11.11 --tag mgmtapi-3_11 --file Dockerfile-oss --target oss311 --platform linux/amd64 .

    #Create a docker image with management api and C* 4.0 (version 4.0.0 and newer are supported)
    docker buildx build --load --build-arg CASSANDRA_VERSION=4.0.1 --tag mgmtapi-4_0 --file Dockerfile-4_0 --target oss40 --platform linux/amd64 .

To build an image based on DSE, see the [DSE README](management-api-agent-dse-6.8/README.md).

### Standalone

    mvn -DskipTests package
    mvn test
    mvn integration-test -Drun311tests=true -Drun40tests=true

**NOTE 1:** Running ````integration-test````s will also run unit tests.

**NOTE 2:** Running ````integration-test````s requires at least one of ````-Drun311tests````, ````-Drun40tests```` or ````-DrunDSEtests```` to be set to ````true```` (you can set any combination of them to ````true````).

**NOTE 3:** In order to run DSE integration tests, you must also enable the ````dse```` profile:

    mvn integration-test -P dse -DrunDSEtests=true

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

  For DSE Docker images, see the [DSE README](management-api-agent-dse-6.8/README.md).

  For running standalone the jars can be downloaded from the github release:
     [Management API Releases Zip](https://github.com/k8ssandra/management-api-for-apache-cassandra/releases)

  The Management API can be run as a standalone service or along with the Kubernetes
  [cass-operator](https://github.com/datastax/cass-operator).

  The Management API is configured from the CLI. To start the service with a C* version built above, run:

     > docker run -p 8080:8080 -it --rm mgmtapi-4_0

     > curl http://localhost:8080/api/v0/probes/liveness
     OK

     # Check service and C* are running
     > curl http://localhost:8080/api/v0/probes/readiness
     OK

## Usage with DSE

Please see the [DSE README](management-api-agent-dse-6.8/README.md) for details.

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

### Gogle Java Style

The project uses [google-java-format](https://github.com/google/google-java-format) and enforces the
[Google Java Style](https://google.github.io/styleguide/javaguide.html) for all Java souce files. The
Maven plugin is configured to check the style during compile and it will fail the compile if it finds
a file that does not adhere to the coding standard.

#### Checking the format

If you want to check the formatting from the command line after making changes, you can simply run:

    mvn fmt:check

NOTE: If you are making changes in the DSE agent, you ened to enable the `dse` profile:

    mvn -Pdse fmt:check

#### Formatting the code

If you want have the plugin format the code for you, you can simply run:

    mvn fmt:format

NOTE: If you are making changes in the DSE agent, you ened to enable the `dse` profile:

    mvn -Pdse fmt:format

#### Using Checkstyle in an IDE

You can also install a checkstyle file in some popular IDEs to automatically format your code. The
Google checkstyle file can be found here: [google_checks.xml](checkstyle/google_checks.xml)

Refer to your IDE's documentation for installing and setting up checkstyle.

### Source code headers

In addtion to Java style formatting, the project also enforces that source files have the correct
header. Source files include `.java`, `.xml` and `.properties` files. The Header should be:

    /*
     * Copyright DataStax, Inc.
     *
     * Please see the included license file for details.
     */

for Java files. For XML and Properties files, the same header should exist, with the appropriate
comment characters replacing the Java comment characters above.

Just like the Coding style, the Headers are checked at compile time and will fail the compie if
they aren't correct.

#### Checking the headers

If you want to check the headers from the command line after making changes, you can simply run:

    mvn license:check

NOTE: If you are making changes in the DSE agent, you ened to enable the `dse` profile:

    mvn -Pdse license:check

#### Formatting the code

If you want have the plugin format the headers for you, you can simply run:

    mvn license:format

NOTE: If you are making changes in the DSE agent, you ened to enable the `dse` profile:

    mvn -Pdse license:format

### XML formatting

The projet also enforces a standard XML format. Again, it is checked at compile time and will fail
the compile if XML files are not formatted correctly. See the plugin documentation for formatting
details here: https://acegi.github.io/xml-format-maven-plugin/?utm_source=mavenlibs.com

#### Checking XML file formatting

If you want to check XML files from the command line after making changes, you can simply run:

    mvn xml-format:xml-check

NOTE: If you are making changes in the DSE agent, you ened to enable the `dse` profile:

    mvn -Pdse xml-format:xml-check

#### Formatting XML files

If you want have the plugin format XML files for you, you can simply run:

    mvn xml-format:xml-format

NOTE: If you are making changes in the DSE agent, you ened to enable the `dse` profile:

    mvn -Pdse xml-format:xml-format

## Design Summary

The architecture of this repository is laid as follows, front-to-back:

1. The `management-api-server/doc/openapi.json` documents the API.
2. The server implements the HTTP verbs/endpoints under the `management-api-server/src/main/java/com/datastax/mgmtapi/resources` folder (e.g. `NodeOpsresources.java`).
3. The server methods communicate back to the agents using `cqlService.executePreparedStatement()` calls which are routed as plaintext through a local socket. These calls return `ResultSet` objects, and to acess scalar values within these you are best to call `.one()` before checking for nulls and `.getObject(0)`. This java objectcan then be serialised into JSON for return to the client.
4. The server communicates only with the `management-api-agent-common` subproject, which holds the unversioned `CassandraAPI` interface.
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

In addition to automatic OpenAPI document generation, a Golang client can be generated during the build by enabling the `clientgen` Maven profile. The client is built using the [OpenAPI Tools generator Maven plugin](https://github.com/OpenAPITools/openapi-generator/tree/master/modules/openapi-generator-maven-plugin) and can be used by Go projects to interact with the Management API. The client generation happens during the `process-classes` phase of the Maven build so that changes to the API implementation can be compiled into an OpenAPI document spec file [during the compile phase](#changes-to-api-endpoints) of the build. The client code is generated in the `target` directory under the [management-api-server](management-api-server) sub-module and should be located at

```sh
management-api-server/target/generated-sources/openapi
```

To generate the client, run the following from the root of the project:

```sh
mvn clean process-classes -Pclientgen
```

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
