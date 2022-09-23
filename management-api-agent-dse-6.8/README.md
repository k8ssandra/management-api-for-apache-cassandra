# Management API with DSE

It is important to note that all DSE dependencies should only be specified in the DSE agent module. No DSE dependencies
can be added to any other projects/modules, as users without access to DSE artifacts won't be able to build the OSSManagement API.

## Building the Management API with DSE

A special `dse` profile was created when building the Management API with DSE dependencies. The required maven command is as following:

```
mvn package -P dse
```

## Running tests for the DSE Agent

To run the project tests against DSE, you need to enable the `dse` profile and specify the property to run DSE tests as follows:

```
mvn verify -P dse -DrunDSEtests
```
