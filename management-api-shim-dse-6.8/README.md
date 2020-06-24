# Management API with DSE

It is important to note that all DSE dependencies should only be specified in the DSE shim project(s). No DSE dependencies
must be added to any other projects, as otherwise users won't be able to build the Management API.

## Building DSE locally and publishing the Jars

```
./gradlew jar publishToMavenLocal -Pversion=6.8.2 -PbuildType=SNAPSHOT
```

## Building the Management API with DSE

A special `dse` profile was created when building the Management API with DSE dependencies. The required maven command is as following:

```
mvn package -P dse
```