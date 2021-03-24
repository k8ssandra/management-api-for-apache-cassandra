# Management API Agent

The Agent is the bridge between the Management API and the instance of Apache Cassandra&trade; or DSE it controls.
This is accomplished by adding the agent jarfile to the startup options of the server with the `-javaagent`
directive. See the [main README](../README.md#using-the-service-with-a-locally-installed-c-or-dse-instance)
as an example.

## Agent Common and Server Specific Agent classes

Ideally, all of the agent code that interacts with Cassandra or DSE would live in a single place. However,
different versions of the server require different implementations. Anything that can be reused across the
server versions should go into the agent-common project here. Anything that requires a different implementation
based on the server needs to go into the server specific project (i.e. agent-3.x, agent-4.x or agent-dse-6.8).

## Duplicated Code in Agent Projects
If you look at the Agent sub projects for [3.x](../management-api-agent-3.x), [4.x](../management-api-agent-4.x)
and [dse-6.8](../management-api-agent-dse-6.8), you will see that some of the code appears to be duplicated.
And you would be correct! While those projects do hold implementation differences, some of the code is
completely duplicated. Normally, this code should be in the common package.

The reason the code is duplicated is due to a change in Cassandra 4.0 [beta3](https://github.com/apache/cassandra/commit/ccab496d2d37c86341d364dea6c27513fda27331#diff-e6e67347a585718be50482cd8ba211647b64f95c543f6e8ab9f15475ba19ee1a)
where [TypeSerializer](https://github.com/apache/cassandra/blob/trunk/src/java/org/apache/cassandra/serializers/TypeSerializer.java#L26)
was changed from an *interface* to an *abstract class*. While TypeSerializer's API didn't change,
and the Agent code using it compiles just fine against either version, it won't work if you don't match
the Cassandra dependency jars with the server you run against. If you compile against 3.x jars and run
against Cassandra 4.0-beta3 or newer, you will get an exception *java.lang.IncompatibleClassChangeError*
indicating that TypeSerializer was found to be a class, but was expected to be an interface. The same
applies to compiling against 4.0-beta3 jars and running against Cassandra 3.x.

Because of this, any Agent code that uses TypeSerializer needs to be split into the sub projects so
that the Agent code can be compiled against the correct class type. *NOTE:* This could happen with any
Cassandra classes that are used by the Agent code, and similar changes would require more refactoring of
common code into server specific sub projects.


