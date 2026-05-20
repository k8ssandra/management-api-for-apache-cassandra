# Management API for Cassandra 5.0

This Maven sub-module if for building a Management API agent that works with Cassandra 5.0.
For building the Agent for the latest trunk versions of Cassandra, see the README in
management-api-agent-6.0.x.

## Building Against Published Cassandra Artifacts

For Cassandra versions that have been publicly released and have Maven artifacts published, you can simply run
the main project Maven build. The pom.xml file in this sub-module should have the `cassandra5.version` property
set to the latest published version (`5.0.8` as of this writing). If you wish to build for a different
published version, for example `5.0.9` when it is released, specify the version:

```sh
mvn package -Dcassandra5.version=5.0.9
```

## Docker image builds

As Management API releases are published, a build of this image will be available in DockerHub at:

    k8ssandra/cass-management-api:5.0.8

## Known Limitations

### MCAC Agent

The latest [MCAC agent](https://github.com/datastax/metric-collector-for-apache-cassandra) does not work with Cassandra trunk.
If you want to use this image with Docker, you must set the environment variable `MGMT_API_DISABLE_MCAC` to `true`:

```sh
docker run -e MGMT_API_DISABLE_MCAC=true k8ssandra/cass-management-api:5.0.8
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

