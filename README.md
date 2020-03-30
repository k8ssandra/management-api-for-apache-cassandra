# Management API for Apache Cassandra

![Java CI](https://github.com/datastax/management-api-for-apache-cassandra/workflows/Java%20CI/badge.svg)
![Docker Release](https://github.com/datastax/management-api-for-apache-cassandra/workflows/Docker%20Release/badge.svg)
## Introduction

   Cassandra operations have historically been command line driven. 
   The management of operational tools for Apache Cassandra have been mostly 
   outsourced to teams who manage their specific environments.  
   
   The result is a fragmented and tribal set of best practices, workarounds,
   and edge cases.
   
   The Management API is a sidecar service layer that attempts to build a well supported
   set of operational actions on Cassandra nodes that can be administered centrally.
   It currently works with official [Apache Cassandra](https://cassandra.apache.org) 3.11.x an 4.0 
   via a drop in java agent.
   
   * Lifecycle Management 
       * Start Node
       * Stop Node
   * Configuration Management (alpha)
       * Change YAML
       * Change jvm-opts
   * Health Checks 
       * Kubernetes liveness/readiness checks
       * Consistency level checks 
   * Per node actions 
       * All nodetool
     
## Design Principles
  * Secure by default
  * Simple to use and extend
  * CQL Only for all C* interactions
    * Operations: Use 'CALL' method for invoking via CQL
    * Observations: Rely on System Views 
  
  The management api has no configuration file, rather, it can only be configured from a 
  small list of command line flags.  Communication by default can only be via unix socket 
  or via a http(s) endpoint with optional TLS client auth.
  
  In a containerized setting the management API represents PID 1 and will be 
  responsible for the lifecycle of Cassandra via the API.
  
  Communication between the Management API and Cassandra is via a local unix socket using
  CQL as it's only protocol.  This means, out of the box Cassandra can be started
  securely with no open ports!  Also, using CQL only means operators can
  execute operations via CQL directly if they wish.
  
  Each Management API is responsible for the local node only.  Coordination across nodes
  is up to the caller.  That being said, complex health checks can be added via CQL.
    
## Building
 Building for containers:
    
    docker build -t management-api-for-apache-cassandra-builder -f ./Dockerfile-build .

    #Create a docker image with management api and C* 3.11
    docker build -t mgmtapi-3_11 -f Dockerfile-3_11 .
    
    #Create a docker image with management api and C* 4.0
    docker build -t mgmtapi-4_0 -f Dockerfile-4_0 .
    
 Building for standalone:
    
    mvn -DskipTests package
    mvn test
    
## REST API
   [The current Swagger/OpenAPI documentation](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/datastax/management-api-for-apache-cassandra/master/management-api-server/doc/openapi.json&nocors)
   
   Also readable from url root: ````/openapi.json````
  
## Usage

  The latest releases are on Docker Hub:
     [Management API for Apache Cassandra 3.11.6](https://hub.docker.com/repository/docker/datastax/cassandra-mgmtapi-3_11_6) and 
     [Management API for Apache Cassandra 4.0 alpha3](https://hub.docker.com/repository/docker/datastax/cassandra-mgmtapi-4_0_0). 

  The Management API can be run as a standalone service or along with the kubernetes 
  [cass-operator](https://github.com/datastax/cass-operator). 
  
  The Management API is configured from the CLI
  
  To start the service with a C* version built above run:
     
     > docker run -p 8080:8080 -it --rm mgmtapi-4_0 
     
     > curl http://localhost:8080/api/v0/probes/liveness
     OK
     
     # Check service and C* are running
     > curl http://localhost:8080/api/v0/probes/readiness
     OK
     
  
  To start the service with a locally installed C* you would run the following:
    
    # REQUIRED: Add management api agent to C* startup
    > export JVM_EXTRA_OPTS="-javaagent:$PWD/management-api-agent/target/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar"
        
    > alias mgmtapi="java -jar management-api-server/target/datastax-mgmtapi-server-0.1.0-SNAPSHOT.jar"
    
    # Start the service with a local unix socket only, you could also pass -H http://localhost:8080 to expose a port
    > mgmtapi --cassandra-socket=/tmp/cassandra.sock --host=unix:///tmp/mgmtapi.sock --cassandra-home=<pathToCassandra>
    
    # Cassandra will be started by the service by default unless you pass --explicit-start flag
    
    # Check the service is up
    > curl --unix-socket /tmp/mgmtapi.sock http://localhost/api/v0/probes/liveness
    OK 
    
    # Check C* is up
    > curl --unix-socket /tmp/mgmtapi.sock http://localhost/api/v0/probes/readiness 
    OK
    
    # Stop C*
    curl -XPOST --unix-socket /tmp/mgmtapi.sock http://localhost/api/v0/lifecycle/stop
    OK
    
  
  The cli help covers the different options:
    
    mgmtapi --help
    
    NAME
            cassandra-management-api - REST service for managing an Apache
            Cassandra node
    
    SYNOPSIS
            cassandra-management-api [ {-C | --cassandra-home} <cassandra_home> ]
                    [ --explicit-start <explicit_start> ] [ {-h | --help} ]
                    [ {-H | --host} <listen_address>... ]
                    [ {-K | --no-keep-alive} <no_keep_alive> ]
                    [ {-p | --pidfile} <pidfile> ]
                    {-S | --cassandra-socket} <cassandra_unix_socket_file>
                    [ --tlscacert <tls_ca_cert_file> ]
                    [ --tlscert <tls_cert_file> ] [ --tlskey <tls_key_file> ]
    
    OPTIONS
            -C <cassandra_home>, --cassandra-home <cassandra_home>
                Path to the cassandra root directory, if missing will use
                $CASSANDRA_HOME
    
                This options value must be a path on the file system that must be
                readable, writable and executable.
    
    
            --explicit-start <explicit_start>
                When using keep-alive, setting this flag will make the management
                api wait to start Cassandra until /start is called via REST
    
            -h, --help
                Display help information
    
            -H <listen_address>, --host <listen_address>
                Daemon socket(s) to listen on. (required)
    
            -K <no_keep_alive>, --no-keep-alive <no_keep_alive>
                Setting this flag will stop the management api from starting or
                keeping Cassandra up automatically
    
            -p <pidfile>, --pidfile <pidfile>
                Create a PID file at this file path.
    
                This options value must be a path on the file system that must be
                readable and writable.
    
    
            -S <cassandra_unix_socket_file>, --cassandra-socket
            <cassandra_unix_socket_file>
                Path to Cassandra unix socket file (required)
    
                This options value must be a path on the file system that must be
                readable and writable.
    
    
            --tlscacert <tls_ca_cert_file>
                Path to trust certs signed only by this CA
    
                This options value must be a path on the file system that must be
                readable.
    
    
            --tlscert <tls_cert_file>
                Path to TLS certificate file
    
                This options value must be a path on the file system that must be
                readable.
    
    
            --tlskey <tls_key_file>
                Path to TLS key file
    
                This options value must be a path on the file system that must be
                readable.
    
    
    COPYRIGHT
            Copyright (c) DataStax 2020
    
    LICENSE
            Please see https://www.apache.org/licenses/LICENSE-2.0 for more
            information

  
## Roadmap
  * CQL based configuration changes
  * Configuration as system table

## License

Copyright DataStax, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
