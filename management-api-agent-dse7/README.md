# Management API with DSE 7 (Converged Core)

It is important to note that all DSE dependencies should only be specified in the DSE agent module. No DSE dependencies
can be added to any other projects/modules, as users without access to DSE artifacts won't be able to build the OSS Management API.

## Maven Settings

In order to build Management API artifacts for DSE (jarfiles and/or Docker images), you will need to have access to the DSE Maven
Artifactory. This will require credentials that should be stored in your `${HOME}/.m2/settings.xml` file. Before attempting any of
the steps below, make sure to copy your Maven settings file to the root of the parent project. Assuming you have a terminal and
are in the root of this git repo:

```sh
cp $HOME/.m2/settings.xml $PWD
```

## Building the Management API with DSE

A special `dse7` profile was created when building the Management API with DSE 7 dependencies. The required maven command is as following:

```sh
mvn package -P dse7
```

## Running tests for the DSE Agent

TODO: The tests have not yet been adapted to run against DSE 7 as this would require copying the DSE 7 Docker image build from DSE repos,
which is an ongoing effort.

## Docker image builds

OUT OF SCOPE: At the moment, no DSE 7 imnages are being built as part of this project. They are built from the DSE repo currently.

### Building DSE images locally

OUT OF SCOPE: At the moment, no DSE 7 imnages are being built as part of this project. They are built from the DSE repo currently.

If you have access to the DSE repository, you can build an image from the DSP-22981 branch. Use the following from the DSE repository root:

```sh
./mvnw clean verify -P binary-distribution
```

### Building a specific version of DSE

NOT APPLICABLE

## Running a locally built image

To run an image you built locally with Management API enabled, run the following:

```sh
docker run -e DS_LICENSE=accept -e USE_MGMT_API=true -p 8080:8080 --name dse my-dse
```

where `my-dse` is the tag of the image you built (you must have access to the DSE repo to build an image).