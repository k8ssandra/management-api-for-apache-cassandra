# Management API for Cassandra trunk

This Maven sub-module if for building a Management API agent that works with Cassandra trunk. Currently,
the version in trunk is 5.0-SNAPSHOT. When 5.0 is released, this sub-module should become a standard
module and a new one created for trunk.

## Build Prerequisites

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

## Building the Agent

Similar to the DSE agent, you have to enable the Maven profile `trunk` in order to build the Agent:

```sh
mvn package -P trunk
```

## Docker image builds

As Management API releases are published, a build of this image will be available in DockerHub at:

    k8ssandra/cass-management-api:5.0.0

### Building images locally

To build a Docker image for yourself, execute the following from the root of the project:

```sh
docker buildx build --load --progress plain --tag cass-trunk --file cassandra-trunk/Dockerfile.ubi8 --target cass-trunk --platform linux/amd64 .
```

You can replace the tag `cass-trunk` with whatever you like.

## Known limitations

The latest [MCAC agent](https://github.com/datastax/metric-collector-for-apache-cassandra) does not work with Cassandra trunk.
If you want to use this image with Docker, you must set the environment variable `MGMT_API_DISABLE_MCAC` to `true`:

```sh
docker run -e MGMT_API_DISABLE_MCAC=true k8ssandra/cass-management-api:5.0.0
```

