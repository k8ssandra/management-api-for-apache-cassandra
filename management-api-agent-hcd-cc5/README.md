# Management API with HCD (Hyper-Converged Database)

It is important to note that all HCD dependencies should only be specified in the HCD agent modules. No HCD dependencies
can be added to any other projects/modules, as users without access to HCD artifacts won't be able to build the OSS Management API.

## HCD versions (hcd-cc4 vs hcd-cc5)

As of this document edit, there are 2 versions of HCD in development. Version 1.1.x is currently maintained on the `hcd-1.1` branch
of the HCD repository. Version 1.2.x is maintained on the `main` branch of the repository. Until recently, HCD 1.2 was based on
Converged Cassandra (Converged Core/CC) 5, while HCD 1.1 is based on CC 4. Soon, HCD 1.2 will switch to CC 4, meaning a future release
of HCD 2.x will be based on CC 5. To make things a little easier to follow from this project's view, as of v0.1.97, the Management
API Agent for HCD will be CC based. This Readme is in the `hcd-cc5` Agent. There is an equivalent one in the `hcd-cc4` Agent. You
must pick the one that your HCD code is based on for it to work properly.

## Maven Settings

In order to build Management API artifacts for HCD (jarfiles and/or Docker images), you will need to have access to the DSE Maven
Artifactory. This will require credentials that should be stored in your `${HOME}/.m2/settings.xml` file.

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

If you have access to the HCD repository, you can build an image from the `main` branch. Use the following from the HCD repository root:

```sh
./mvnw clean package
```

## Running a locally built image

To run an image you built locally with Management API enabled, run the following:

```sh
docker run -e DS_LICENSE=accept -e USE_MGMT_API=true -p 8080:8080 --name hcd my-hcd
```

where `my-hcd` is the tag of the image you built (you must have access to the BDP repo to build an image).