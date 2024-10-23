# Management API for Cassandra 5.0

This Maven sub-module if for building a Management API agent that works with Cassandra 5.0. Currently,
the version in trunk is 5.1-SNAPSHOT, and the artifacts produced by this project work with 5.0.x, as well
as the 5.1-SNAPSHOT version of Cassandra in trunk.

## Building Against Published Cassandra Artifacts

For Cassandra versions that have been publicly released and have Maven artifacts published, you can simply run
the main project Maven build. The pom.xml file in this sub-module should have the `cassandra5.version` property
set to the latest published version (`5.0.2` as of this writing). If you wish to build for a different
published version, for example `5.0.3` when it is released, specify the version:

```sh
mvn package -Dcassandra5.version=5.0.3
```

## Building Against Cassandra Trunk

To build the agent against the latest trunk, you will need to build the Cassandra 5 Maven artifacts first and
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
ant mvn-install
```

This should setup your local Maven repository with the cassandra-all.jar artifacts that you will need to build the Agent.

### Building the Agent

As of this writing, Cassandra trunk sits at version 5.1-SNAPSHOT. So if you have followed the previous Cassandra build
steps, you will need to override the `cassandra5.version` property when building the agent:

```sh
mvn package -Dcassandra5.version=5.1-SNAPSHOT
```

## Docker image builds

As Management API releases are published, a build of this image will be available in DockerHub at:

    k8ssandra/cass-management-api:5.0.2

### Building Images Locally for Cassandra trunk

To build a Docker image for yourself, execute the following from the root of the project:

```sh
docker buildx build --load --progress plain --tag cass-trunk --file cassandra-trunk/Dockerfile.ubi8 --target cass-trunk --platform linux/amd64 .
```

You can replace the tag `cass-trunk` with whatever you like.

## Known Limitations

The latest [MCAC agent](https://github.com/datastax/metric-collector-for-apache-cassandra) does not work with Cassandra trunk.
If you want to use this image with Docker, you must set the environment variable `MGMT_API_DISABLE_MCAC` to `true`:

```sh
docker run -e MGMT_API_DISABLE_MCAC=true k8ssandra/cass-management-api:5.0.2
```

