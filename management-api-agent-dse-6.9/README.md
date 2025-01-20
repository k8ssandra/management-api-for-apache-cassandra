# Management API with DSE

It is important to note that all DSE dependencies should only be specified in the DSE agent module. No DSE dependencies
can be added to any other projects/modules, as users without access to DSE artifacts won't be able to build the OSS Management API.

## Maven Settings

In order to build Management API artifacts for DSE (jarfiles and/or Docker images), you will need to have access to the DSE Maven
Artifactory. This will require credentials that should be stored in your `${HOME}/.m2/settings.xml` file.

## Building the Management API with DSE

A special `dse` profile was created when building the Management API with DSE dependencies. The required maven command is as following:

```sh
mvn package -P dse
```

## Running tests for the DSE Agent

To run the project tests against DSE, you need to enable the `dse` profile and specify the property to run DSE tests as follows:

```sh
mvn verify -P dse -DrunDSE6.9tests
```

To run the test suite using the UBI based image:

```sh
mvn verify -P dse -DrunDSE6.9testsUBI
```

## Docker image builds

Official DSE Docker images are built from the [docker-images](https://github.com/riptano/docker-images) repo. Those images are published
to DockerHub at:

    datastax/dse-server

Images built from this repo are for testing changes to Management API prior to releasing new versions and integrating them into
official DSE images. Images built from this repo will be published to DockerHub at:

    datastax/dse-mgmtapi-6_8

### Building DSE images locally

Building DSE images locally requires the [buildx](https://docs.docker.com/build/buildx/install/) Docker plugin.

DSE 6.9 images can be built based on Ubuntu or RedHat's UBI8 base images. To build an Ubuntu based image, run the following from the root of the parent project:

```sh
docker buildx build --load --progress plain --tag my-dse --file dse/Dockerfile-dse6.9.jdk11 --target dse --platform linux/amd64 .
```

where `my-dse` is whatever tag you want to use for your image.

Likewise, to build a UBI8 based image, run:

```sh
docker buildx build --load --progress plain --tag my-dse --file dse/Dockerfile-dse6.9.ubi8 --target dse --platform linux/amd64 .
```

### Building a specific version of DSE

By default, the DSE version for the image build will be the latest released version of DSE. If you wish to build an image for a
specific DSE version, specify the `DSE_VERSION` build-arg:

```sh
docker buildx build --load --build-arg DSE_VERSION=6.9.0 --progress plain --tag my-dse --file dse/Dockerfile-dse6.9.jdk11 --target dse --platform linux/amd64 .
```

## Running a locally built image

To run an image you built locally with Management API enabled, run the following:

```sh
docker run -e DS_LICENSE=accept -e USE_MGMT_API=true -p 8080:8080 --name dse my-dse
```

where `my-dse` is the tag of the image you built.