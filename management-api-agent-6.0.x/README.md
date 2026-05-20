# Management API for Cassandra trunk

This Maven sub-module if for building a Management API agent that works with Cassandra trunk. Currently,
the version in trunk is 7.0-SNAPSHOT, and the artifacts produced by this project work with the 7.0-SNAPSHOT
version of Cassandra in trunk.

## Building Against Published Cassandra Artifacts

For Cassandra versions that have been publicly released and have Maven artifacts published, you can simply run
the main project Maven build. The pom.xml file in this sub-module should have the `cassandra.version` property
set to the latest published version (`7.0-SNAPSHOT` as of this writing). If you wish to build for a different
published version, for example `7.0.1` when it is released, specify the version:

```sh
mvn package -Dcassandra.version=7.0.1
```

## Building Against Cassandra Trunk

To build the agent against the latest trunk, you will need to build the Cassandra trunk Maven artifacts first and
install them locally. This may or may not be necessary in some cases, depending on what changes have occurred
in the Cassandra code base since the latest published version of Cassandra artifacts.

### Build Prerequisites

In order to build this module, you must have Cassandra trunk artifacts compiled and installed in your
local Maven repository. Luckily, Cassandra's build has a target that will do just that: `mvn-install`.
To setup the artifacts:

1. Clone the Apache Cassandra repo locally

```sh
git clone https://github.com/apache/cassandra.git
```

2. Run the `mvn-install` target

```sh
cd cassandra
ant mvn-install -Dno-javadoc=true
```

This should setup your local Maven repository with the cassandra-all.jar artifacts that you will need to build the Agent.

### Building the Agent

As of this writing, Cassandra trunk sits at version 7.0-SNAPSHOT. To build the agent, you will need to specify the `trunk`
Maven profile, as this agent is not built by default:

```sh
mvn package -P trunk
```

## Docker image builds

As Management API releases are published, a build of this image will be available in DockerHub at:

    k8ssandra/cass-management-api:7.0

**NOTE:** These images won't be published unti Cassandra 7.0 goes GA.

### Building Images Locally for Cassandra trunk

To build a Docker image for yourself, execute the following from the root of the project:

```sh
docker buildx build --load --progress plain --tag cass-trunk --file cassandra-trunk/Dockerfile.ubi --target cassandra --platform linux/amd64 .
```

You can replace the tag `cass-trunk` with whatever you like.

## Known Limitations

### MCAC Agent

The latest [MCAC agent](https://github.com/datastax/metric-collector-for-apache-cassandra) does not work with Cassandra trunk.
If you want to use this image with Docker, you must set the environment variable `MGMT_API_DISABLE_MCAC` to `true`:

```sh
docker run -e MGMT_API_DISABLE_MCAC=true k8ssandra/cass-management-api:7.0
```

### Netty libraries for Metrics

Cassandra 5.0 and newer releases have excluded `netty-codec-http` from its `netty-all` dependency. Unfortunately,
the Metrics endpoint implementation in this project (that comes with all of the Agents) relies on this library.

Normally, solving this problem would be to explicitly include `netty-codec-http` in the Agent uber jar. This creates
a problem however in that the Agent would have to include the specific `netty-codec-http` version that matches the
version of Netty libraries Cassandra ships with. The Cassandra Netty version is not static across all releases in a
given `major`.`minor`, further complicating the issue.

The solution is:
- Explicitly include `netty-codec-http` in the Agent pom.xml
- Mark `netty-codec-http` as "provided" even though it's not in all versions of Cassandra
- Update the Docker image build to include the necessary `netty-codec-http` artifact in /opt/cassandra/lib

This solution works for the images provided by this repository. However, the Agent jarfiles that are published at
release will no longer contain this library. That means that if you use just the published Agent jarfile (and do
not use the published Docker images), the Metrics endpoint will not work unless you also add the missing
`netty-codec-http` library.
