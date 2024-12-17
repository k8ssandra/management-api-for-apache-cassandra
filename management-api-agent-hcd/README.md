# Management API with HCD (Hyper-Converged Database)

It is important to note that all HCD dependencies should only be specified in the HCD agent modules. No HCD dependencies
can be added to any other projects/modules, as users without access to HCD artifacts won't be able to build the OSS Management API.

## HCD versions

As of this document edit, there are 2 versions of HCD in development. Version 1.0.x is currently maintained on the `hcd-1.0` branch
of the HCD repository. Version 1.2.x is maintained on the `main` branch of the repository. The major difference between the two
versions is the Converged Cassandra Core that is used. HCD 1.0.x uses Converged Core 4, while HCD 1.2.x uses Converged Core 5. As
with Cassandra versions, the HCD agent has to be broken into 2 sub-modules for compiling compatibility. The version in this
sub-module is for HCD 1.0.x. For HCD 1.2.x, use the agent in sub-module `management-api-agent-hcd-1.2.x`.

## Maven Settings

In order to build Management API artifacts for HCD (jarfiles and/or Docker images), you will need to have access to the DSE Maven
Artifactory. This will require credentials that should be stored in your `${HOME}/.m2/settings.xml` file. Before attempting any of
the steps below, make sure to copy your Maven settings file to the root of the parent project. Assuming you have a terminal and
are in the root of this git repo:

```sh
cp $HOME/.m2/settings.xml $PWD
```

## Building the Management API with HCD

A special `hcd` profile was created when building the Management API with HCD dependencies. The required maven command is as following:

```sh
mvn package -P hcd
```

## Running tests for the HCD Agent

TODO: The tests have not yet been adapted to run against HCD as this would require copying the HCD Docker image build from DSE repos,
which is an ongoing effort.

## Docker image builds

OUT OF SCOPE: At the moment, no HCD images are being built as part of this project. They are built from the HCD repo currently.

### Building HCD images locally

OUT OF SCOPE: At the moment, no HCD images are being built as part of this project. They are built from the HCD repo currently.

If you have access to the HCD repository, you can build an image from the `hcd-1.0` branch. Use the following from the HCD repository root:

```sh
./mvnw clean verify
```

### Building a specific version of HCD

HCD versions are maintained in branch names with the format `hcd-<major>.<minor>` (for example `hcd-1.1`). The latest/current version
pf HCD will be in the `main` branch (version 1.2.x as of this edit). Building a specific versions of HCD simply requires you to checkout
the version bracnh (or `main` if you wanto build the latest version) and build as above.

## Running a locally built image

To run an image you built locally with Management API enabled, run the following:

```sh
docker run -e DS_LICENSE=accept -e USE_MGMT_API=true -p 8080:8080 --name hcd my-hcd
```

where `my-hcd` is the tag of the image you built (you must have access to the BDP repo to build an image).