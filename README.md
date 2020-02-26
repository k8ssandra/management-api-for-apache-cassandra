# Management API for Apache Cassandra

### Introduction

   Cassandra operations have historically been command line driven. 
   The management of operational tools for Apache Cassandra have been mostly 
   outsourced to teams who manage their specific environments.  
   
   The result is a fragmented and tribal set of best practices, workarounds,
   and edge cases.
   
   The Management API is a sidecar service layer that attempts to build a well supported
   set of operational actions on Cassandra nodes that can be administered centrally.
   
   * Lifecycle Management
   * Configuration Management
   * Health Checks
   * Per node actions (all nodetool)
     
### Design Principles
  * Secure by default
  * Simple to use and extend
  * CQL Only
  * Cloud native

  The management api has no configuration file, rather, it can only be configured from a 
  small list of command line flags.  Communication can be local only via unix socket 
  or via a http(s) endpoint with optional TLS client auth.
  
  In a containerized setting the management API represents PID 1 and will be 
  responsible for the lifecycle of Cassandra via the API.
  
  Communication between the Management API and Cassandra is via a local unix socket using
  CQL as it's only protocol.  This means, out of the box Cassandra can be started
  securely with no open ports!  Also, using CQL only means operators can
  execute operations via CQL directly if they wish.
  
  Each Management API is responsible for the local node only.  Coordination across nodes
  is up to the caller.  That being said, complex health checks can be added via CQL.
  

### Building

    mvn package -DskipTests
    docker build -t mgmtapi .
    docker run --name mgmtapi -it --rm mgmtapi
    
### API
   [The current Swagger/OpenAPI documentation](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/datastax/management-api-for-apache-cassandra/master/management-api-server/doc/openapi.json&nocors)
   *(Won't work till repo is OSS)*
    
### Roadmap
  * CQL based configuration changes
  * Configuration as system table
