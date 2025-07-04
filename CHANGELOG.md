# Changelog

Changelog for Management API, new PRs should update the `main / unreleased` section with entries in the order:

```markdown
* [CHANGE]
* [FEATURE]
* [ENHANCEMENT]
* [BUGFIX]
```

## unreleased
* [FEATURE] [#652](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/652) Add Cassandra 4.0.18 to the build matrix
* [FEATURE] [#651](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/651) Add Cassandra 4.1.9 to the build matrix
* [FEATURE] [#649](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/649) Add DSE 6.8.58 to the build matrix
* [FEATURE] [#650](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/650) Add DSE 6.9.10 to the build matrix
* [FEATURE] [#654](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/654) Add DSE 6.9.11 to the build matrix
* [FEATURE] [#656](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/656) Add DSE 6.8.59 to the build Matrix
* [BUGFIX] [#657](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/657) HCD 1.1.2 fails to start

## v0.1.104 [2025-05-16]
* [ENHANCEMENT] [#644](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/644) Upgrade Logback dependency to 1.5.13
* [FEATURE] [#641](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/641) Add DSE 6.8.57 to the build matrix
* [FEATURE] [#640](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/640) Add DSE 6.9.9 to the build matrix

## v0.1.103 [2025-05-03]
* [FEATURE] [#635](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/635) Add Cassandra 5.0.4 to the build matrix
* [FEATURE] [#636](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/636) Add JDK17 for Cassandra 5.0 images
* [FEATURE] [#631](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/631) Add DSE 6.8.55 to the build matrix
* [FEATURE] [#632](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/632) Add DSE 6.8.56 to the build matrix
* [FEATURE] [#638](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/638) Add DSE 6.9.8 to the build matrix

## v0.1.102 [2025-04-01]
* [ENHANCEMENT] [#626](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/626) Bump MCAC to 0.3.6
* [BUGFIX] [#629](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/629) Remove writable requirement from $HOME in the DSE images

## v0.1.101 [2025-03-26]
* [ENHANCEMENT] [#624](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/624) Add Maven Enforcer plugin to keep dependency versions in sync
* [BUGFIX] [#577](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/577) pool_name can include dashes and slashes.

## v0.1.100 [2025-03-24]
* [ENHANCEMENT] [#620](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/620) Update Dependnecies
* [BUGFIX] [#438](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/438) Add Cassandra 5.1 Agent
* [BUGFIX] [#617](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/617) Fix Cassandra trunk builds

## v0.1.99 [2025-03-10]
* [BUGFIX] Create notificationListener before any repairs are submitted (#616)
* [BUGFIX] Properly apply parallelism in the repair specs (#615)

## v0.1.98 [2025-02-24]
* [BUGFIX] [#611](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/611) Fix HCD CC4 Agent for newer Netty

## v0.1.97 [2025-02-20]
* [CHANGE] Remove Cassandra 4.0.16 from the build matrix due to a regression (https://issues.apache.org/jira/browse/CASSANDRA-20090)
* [CHANGE] [#608](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/608) Refactor HCD Agents (cc4 vs cc5)
* [FEATURE] [#601](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/602) Add Cassandra 4.0.17 to the build matrix
* [FEATURE] [#603](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/603) Add DSE 6.8.54 to the build matrix
* [FEATURE] [#604](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/604) Add DSE 6.9.7 to the build matrix

**NOTE**: As of this release, the HCD Agent jarfiles are renamed to include the Converged Cassandra version (-cc4 or -cc5)

## v0.1.96 [2025-02-05]
* [FEATURE] [#600](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/600) Add Cassandra 5.0.3, 4.1.8, 4.0.16 to the build matrix
* [BUGFIX] [#598](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/598) Update HCD 1.2 Agent
* [ENHANCEMENT] Update SLF4J to version 2.0.9
* [ENHANCEMENT] Update Logback to version 1.4.14

## v0.1.95 (2025-02-03)
* [BUGFIX] [#XXX] Change "/bin/which" command to simply "which" (no issue filed)

## v0.1.94 (2025-01-30)
* [BUGFIX] [#596](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/596) Add symlink to /tmp/dse.sock for DSE images

## v0.1.93 (2025-01-29)
* [BUGFIX] [#593](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/593) Update HCD 1.2 Agent for CC 5.0.2
* [ENHANCEMENT] [#432](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/432) Improve version management

## v0.1.92 (2025-01-20)
* [FEATURE] [#589](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/589) Add DSE 6.9.6 to the build matrix
* [BUGFIX] [#590](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/590) Fix Artifact release to also publish HCD 1.2 Agent

## v0.1.91 (2025-01-10)
* [FEATURE] [#573](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/573) Add support for HCD 1.2
* [FEATURE] [#578](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/578) Add DSE 6.9.5 to build matrix
* [FEATURE] [#582](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/582) Add DSE 6.8.53 to build matrix
* [ENHANCEMENT] [#579](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/579) Add /opt/dse/resources/cassandra/tools/bin to the default PATH for sstable tools
* [ENHANCEMENT] [#574](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/574) Support consistency parameter in the /start when replacing a DSE node. 

## v0.1.90 (2024-11-22)
* [FEATURE] [#566](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/566) Add listRoles and dropRole functionality to the REST interface
* [FEATURE] [#571](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/571) Add Cassandra 4.0.15 to the build matrix
* [FEATURE] [#569](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/569) Add DSE 6.9.4 to the build matrix
* [FEATURE] [#568](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/568) Add DSE 6.8.52 to the build matrix

## v0.1.89 (2024-10-29)
* [BUGFIX] [#564](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/564) Fix LatencyMetrics for DSE

## v0.1.88 (2024-10-28)
* [CHANGE] [#556](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/556) Update Management API dependencies to address CVEs
* [CHANGE] [#547](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/547) Deprecate Cassandra 3.11 support
* [FEATURE] [#551](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/551) Add Cassandra 5.0.2 to the build matrix
* [FEATURE] [#549](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/549) Add DSE 6.9.3 to the build matrix
* [ENHANCEMENT] [#552](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/552) Improve "liveness" probe implementation
* [BUGFIX] [#553](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/553) Fix CassandraTaskExports metric filtering to make it work with 5.0.x Major compactions
* [BUGFIX] [#560](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/560) Fix LatencyMetrics bucketing on 4.1 and 5.0 as their reservoir stores the data in microseconds, not nano (unlike 3.11 and 4.0)
* [BUGFIX] [#562](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/562) Fix version detection

## v0.1.87 (2024-10-02)
* [FEATURE] [#535](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/535) Add Cassandra 5.0.0 to the build matrix
* [BUGFIX] [#541](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/541) readOnlyRootFilesystem still fails due to some Spark folders in OpenShift
* [FEATURE] [#543](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/543) Add Cassandra 5.0.1 to the build matrix
* [FEATURE] [#544](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/544) Add Cassandra 4.0.14 to the build matrix
* [FEATURE] [#545](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/545) Add Cassandra 4.1.7 to the build matrix
* [FEATURE] [#534](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/534) Add DSE 6.8.51 to the build matrix

## v0.1.86 (2024-09-17)
* [ENHANCEMENT] [#539](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/539) Add the ability to run DSE 6.8 and 6.9 in readOnlyRootFilesystem mode

## v0.1.85 (2024-09-03)
* [FEATURE] [#523](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/523) Trust store reload functionality for DSE only (not Cassandra)
* [FEATURE] [#527](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/527) Add Cassandra 4.1.6 to the build matrix
* [FEATURE] [#522](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/522) Add DSE 6.9.1 to the build matrix
* [FEATURE] [#529](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/529) Add DSE 6.9.2 to the build matrix
* [ENHANCEMENT] [#521](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/521) Add management-api to Cassandra conf in the Dockerfile, not entrypoint for DSE 6.9, Cassandra 4.1 and Cassandra 5.0. This allows to run the container with readOnlyRootFilesystem.
* [BUGFIX] [#524](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/524) Fix HintsService Hint_delays- metrics parsing and ReadCoordination metrics parsing
* [BUGFIX] [#520](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/520) Update DSE 6.9.0 dependnecy
* [BUGFIX] [#531](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/531) Fix DSE 6.9 UBI image agent loading

## v0.1.84 (2024-07-11)
* [FEATURE] [#517](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/517) Add DSE 6.9.0 to the build matrix
* [ENHANCEMENT] [#516](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/516) Address warnings in Dockerfiles

## v0.1.83 (2024-07-10)
* [BUGFIX] [#510](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/510) CDC is not working in the OSS images (at least not UBI)
* [BUGFIX] [#507](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/507) Fix the SSLContext reloading in the initChannel when certificates have changed.
* [FEATURE] [#511](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/511) Add DSE 6.8.50 and DSE 6.9.0-rc.3 to the build matrix
* [BUGFIX] [#512](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/512) Add relabeling rules for internode connection metrics (inbound and outbound)

## v0.1.82 (2024-06-14)
* [FEATURE] [#504](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/504) Add DSE 6.9.0-rc.1 to the build matrix

## v0.1.81 (2024-06-13)
* [FEATURE] [#497](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/497) Add DSE 6.8.49 to the build matrix
* [BUGFIX] [#502](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/502) Fix DSE 6.9 UBI image builds
* [BUGFIX] [#493](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/493) Fix nodetool in UBI based images

## v0.1.80 (2024-06-13)
* [FEATURE] [#496](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/496) Add DSE 6.9 image builds

## v0.1.79 (2024-05-31)
* [FEATURE] [#487](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/487) Add DSE 6.8.48, Cassandra 4.1.5 and Cassandra 4.0.13 to the build matrix

## v0.1.78 (2024-05-16)
* [CHANGE] [#486](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/486) Support HCD

## v0.1.77 (2024-05-15)
* [BUGFIX] [#480](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/480) python 2 missing on ubi cass-management-api images 3.11
* [CHANGE] [#484](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/484) Rename DSE7 to HCD

## v0.1.76 (2024-05-03)
* [BUGFIX] [#477](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/477) Fix cassandra-topology.properties removal
* [FEATURE] [#475](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/475) Add DSE 6.8.47 to the build matrix

## v0.1.75 (2024-04-25)
* [FEATURE] [#474](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/474) Add DSE 6.8.46 to the build matrix
* [FEATURE] [#461](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/461) Add Cassandra 5.0-beta1 tot he build matrix
* [FEATURE] [#470](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/470) Add Cassandra 4.0.12 to the build matrix
* [FEATURE] [#467](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/467) Add DSE 6.8.44 to the build matrix
* [FEATURE] [#469](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/469) Add Cassandra 3.11.17 to the build matrix
* [ENHANCEMENT] [#465](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/465) Add tar to the DSE images
* [BUGFIX] [#463](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/463) Remove cassandra-topology.properties from the configuration folder

## v0.1.74 (2024-03-21)
* [FEATURE] [#453](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/453) Use a longer driver timeout for drain
* [FEATURE] [#455](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/455) Add DSE 6.8.43 to the build matrix
* [FEATURE] [#458](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/458) Update MCAC to v0.3.5
* [ENHANCEMENT] [#441](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/441) Replace ShellUtils arbitrary command execution

## v0.1.73 (2024-02-20)

* [FEATURE] [#449](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/449) Add DSE 6.8.42 to the build matrix
* [FEATURE] [#451](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/451) Add Cassandra 4.1.4 to the build matrix
* [BUGFIX] [#448](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/448) Scrub did not work when targetting all the keyspaces
* [BUGFIX] [#437](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/437) Compaction threw NPE if used without target TokenRange

## v0.1.72 (2023-12-19)

* [BUGFIX]  [#434](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/434) Temporarily disable arm64 support for DSE images
* [FEATURE] [#696](https://github.com/thelastpickle/cassandra-medusa/issues/696) Add endpoint for rebuilding DSE search indices
* [FEATURE] [#439](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/439) Add DSE 6.8.41 to the build matrix

## v0.1.71 (2023-11-16)
* [CHANGE]  [#425](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/425) Shade Jackson libraries in agents
* [BUGFIX]  [#413](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/413) UBI8 images do not terminate correctly
* [BUGFIX]  [#360](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/360) Support Cassandra 5.0-alpha1
* [BUGFIX]  [#415](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/415) Some jvm_* metrics aren't labelled correctly
* [BUGFIX]  [#422](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/422) Metrics endpoint fails to start for some DSE images
* [FEATURE] [#398](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/398) Make Management API port configurable
* [FEATURE] [#419](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/419) Add DSE 6.8.40 to the build matrix
* [FEATURE] [#397](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/397) Add Cassandra 4.0.11, 4.1.3 5.0-alpha2

## v0.1.70 (2023-10-17)
* [BUGFIX]  [#411](https://github.com/k8ssandra/management-api-for-apache-cassandra/pull/411) Serialize long types as Strings to avoid float64 conversion

## v0.1.69 (2023-10-13)
* [CHANGE]  [#400](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/400) Ensure curl and wget are installed in images
* [FEATURE] [#326](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/326) Provide topology endpoints in OpenAPI client
* [FEATURE] [#345](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/345) Implement host related methods
* [FEATURE] [#361](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/361) Add new listTables operation with more detailed response
* [FEATURE] [#342](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/342) New mechanism to allow status tracking of repairs via repair ID. Both v1 and v2 endpoints now return a repair ID for this purpose.
* [FEATURE] [#405](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/405) New v2 Repairs endpoint which takes more parameters, new endpoint to force terminate all repairs.
* [FEATURE] [#374](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/374) New endpoint to provide a token range to endpoint mapping.
* [FEATURE] [#372](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/372) Add IS_LOCAL field to /metadata/endpoints response.
* [FEATURE] [#403](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/403) Add async versions of /flush and /garbagecollect endpoints under /api/v1
* [FEATURE] [#401](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/401) Add DSE 6.8.39

## v0.1.68 (2023-10-09)
* [FEATURE] [#293](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/293) Expose running tasks as metrics

## v0.1.67 (2023-09-29)
* [CHANGE]  [#323](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/323) Rename Maven artifact groupId from com.datastax to io.k8ssandra
* [FEATURE] [#323](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/323) Add OpenAPI Java client generation
* [FEATURE] [#337](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/337) Publish Maven artifacts to Cloudsmith.io
* [FEATURE] [#375](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/375) Add SSL/TLS hot reloading and allow TLS v1.3
* [FEATURE] [#349](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/349) Metrics endpoint should allow querying a subset
* [FEATURE] [#395](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/395) Add Cassandra version 3.11.16
* [FEATURE] [#380](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/380) Add DSE 6.8.38
* [BUGFIX]  [#339](https://github.com/k8ssandra/management-api-for-apache-cassandra/issues/339) OpenAPI client publish does not also publish other artifacts

## Releases older than this point did not maintain a Changelog in the format above. The changes below were generated by the gen_changelog.sh script.
## v0.1.66 (2023-07-25)

*  Disable Maven package publishing [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/cdedd161d79753175561a67e6f626563273342b7)
*  Add UBI based images for OSS Cassandra versions (#313) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/bbc79f2cd56f8fbdacbed04983a5c8385d35759d)
*  Add missing TZ data for JDK11 UBI images [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/c6c9fac4d6eb9566a68080433d14ee055bd19bb9)
*  Fix GitHub Packages publishing [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/e81db97ffa4dec36d06458e360911447632a9223)
*  Improve CI testing so that tests are duplicated for every PR commit [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/4c62959c813866dcd968ed34dd3d356cc7df8ad7)
*  Skip javadoc generation for Cassandra trunk images [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b1bc077b8e346d26dc2052e2eb151aa3003acbf4)


## v0.1.65 (2023-07-12)

*  Upgrade DSE UBI images to 8 for ARM64 support (#320) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b0dd6354caf5f34792e0dd91633203d2e95c4868)


## v0.1.64 (2023-07-12)

*  Add ARM64 image builds for DSE (#315) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/d94b986095e8b1d0ab8d3c1e2aa366035186a549)
*  Fix multi-arch DSE image builds (#317) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/586d65e25c1878e4a530328019cfbe037a640f91)
*  Add ARM64 builds for all DSE images [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/5b8cd600118b62a34905f00d82e6dff1c64ce299)


## v0.1.63 (2023-06-30)

*  Fix file ownership and permissions in images (#310) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/84f5c8f0e0bfb1a0dd4d56a908daf583b4a3a61a)


## v0.1.62 (2023-06-21)

*  Add SHA tag to nightly builds (#299) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a3188bad81b36e321d5992bfb15f2745b6943888)
*  Fix nightly SHA build tag [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/bbd8b8372233e00e44aa56551169a4ec89732b7c)
*  Fix event detection typo [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/50f3b4099ffb883e66a16dce05de8d7be511994c)
*  Add ability to build Cassandra trunk images for a specific SHA commit (#300) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/dd3089e549b9623fbc26ea20a0938569cd9d908d)
*  fix nightly build SHA tags [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ed6f13387ef8b3cdb32827305bc8ea30c33cc8ee)
*  fix typo for default SHA commit [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a7a7c06a98849044f749f23bbff84f520a31c263)
*  Separate scheduled and workflow_dispatch commit SHA determination [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/17adcb6189f8edd1d2c44e8c86b4b9507ea7b687)
*  Add Cassandra version 4.0.10 and 4.1.2 as well as DSE 6.8.36 (#306) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ac2c3f3b5c24ae72acfe31f440fa4c18b796a309)
*  Add DSE7 agent (#307) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/d715657065bcf82934941527bb0e3f840e4f5225)
*  Fix Jar publish for DSE7 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a59b8e494126ba2fd5ec32f529717823fdbcad3c)
*  Really fix DSE7 agent publish [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/206519c35d53683672d6d52f70703297362b8ce2)


## v0.1.61 (2023-05-11)

*  Parse tokens in endpoint states response (#294) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/55a03f2ef4a8a2ac972c289526814123df9c9229)
*  Add Cassandra 3.11.15 and DSE 6.8.35 (#296) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/c6b968311f877b34ee189df4404c20210a1e6dab)
*  Update CDC agenet to v2.2.9 (#297) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/198f45c21e4fbfb8fed3d8989aed0784c3b69fd2)


## v0.1.60 (2023-04-18)

*  Update Cassandra trunk from 4.2 to 5.0 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/8e46a09bd0d5e49c3dac679ce728367257a3b9a7)
*  Allow Cassandra trunk image to be built with workflow_dispatch [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/38af1c11d989c92219257f8f08b99b7fd8ff3bc1)
*  Rename Cassandra trunk GHA job [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/bc646260184e3da70e2dfbf213a19623abe6d56a)
*  Add DSE 6.8.34 (#287) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/15049a01a035467dbd1407e7a2fea040dc278a8c)
*  Disable FOSSA scan [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0b39eb30f46bf4ff2989b7a44300691b254108ed)
*  Add Cassandra 4.1.1 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/13c83f7b3e01a8b845b1edb3f6191ca4376462c4)
*  Add Cassandra 4.0.9 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/c895abdb7466eecded58f1287b057e4406e4f7a4)


## v0.1.59 (2023-03-07)

*  Modify name parsing to do cleaning after every relabel rule (#278) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/2eee0760f215cda1c075b5b87eeae6fa300dd2be)
*  Update Maven artifact deployment job [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/d50ebb4f6474e18acd6a243940fe9d0b01736d00)
*  Add DSE 6.8.33 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0bd02f8ca6d5a11c342f4940dcd637f867bf800c)
*  Make OSS versions a string for build matrix [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/1c7ac075573a54334a070c5a5afe541f2aa5429f)
*  Update DSE agent version [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ccabe9922c0657e1fa5a0eb97ba9864139a33bc6)


## v0.1.58 (2023-03-06)

*  Remove the use of set-output in GHA and upgrade actions using deprecated deps (#261) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b8db70ba2c988e107d38a94e752187db02affa44)
*  Add google-java-format (#262) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/cca7bd06e2dd18a6ce905ccad8a28778c11d5d7f)
*  Update project to require JDK11 (#264) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/e7a45356b2a5825e1bcad462826dce04c1500952)
*  Fix flaky test (#265) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f6ae10130d2d29ad335a76e4971a3ec055976839)
*  Revert f6ae10130d2d29ad335a76e4971a3ec055976839 (#266) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/8867854d98abb9c12d7e19bf7b27f927a4e1ee01)
*  Initial add of Cassandra trunk images (#263) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/38c1fa1e41ace50f9214e025883cd561a9b7343c)
*  Fix Cassandra trunk Dockerfile [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/9630951ae05ad2a9a03be7e2b42cdead75f63293)
*  Change Cassandra trunk build to a cron [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/3f9bedfb525f85161792e91be2b5b9017d56f4e5)
*  Remove log that causes people to panic (#267) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/1e53f2a100f215195d80064e65ab5fbcbeaeebc5)
*  Add DSE 6.8.32 (#269) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ed3cb920f6d715518d08fceb0fb81ec307023ee1)
*  Revert to nearform action as the recommended nearform-actions action does not exist [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/3c912b8dfa7b86b451c9e3395fa649f53f27cf17)
*  Support running tests against Casandra trunk images and update JDK to 11 (#272) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b4475322392c262ad0ffed480ff5e2250e53f57a)
*  Hardcode Cassanra trunk version for now [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f93f12fb94a59d47e7b8fbd7bc23e13ed90fd9c8)
*  Replace tag value instead of adding a duplicate (#273) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ac5d7fd17200e111b55656442df195c142adea7f)
*  Add Cassandra 4.0.8 (#276) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0ebff83acbad897d1946693062ab1d117b93038a)
*  Prevent server crash for uninitialized metrics (#277) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/4f9107784e48051b30c99f1fc31129b2081f4744)
*  Remove Cassandra trunk artifacts from release process [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/e3a463658183a3fcc643967f4c558bb1e06e512f)


## v0.1.57 (2023-02-03)

*  Ignore when the CassandraVersion class isn't found to allow DSE to start (#259) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/2b4b8df53ee852edd17df3393d5f80fd0fcbc697)
*  Support only 3.11.13 and newer in 3.11 branch and 4.0.4 or newer in 4 (#260) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/7d3131f56e7cf62edf252dcbea1875d5fe2cabd3)


## v0.1.55 (2023-02-02)

*  Modify field name from source_labels to sourceLabels. (#250) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ea6f0a069c89e50231ba258c4bbe2bc159094609)
*  Reorder the clean() and sanitizeMetricName in the metricName parser. (#252) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/4df9332c5bb788046f4c5eaf9db3ec3fcd68608e)
*  Add sum to timer, add logging to debug missing histogram (#254) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b8d3613146f92fcc4de416f3cc5c60b68f645f7b)
*  Add replacements relabeling (#256) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f85032fa787a4e2a42a37ad16a94eeefa70ff907)
*  3.11 compatibility improvements (#257) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/8b3fd9e56827254f3c32199233ec03d0f9027b73)


## v0.1.54 (2023-01-19)

*  Add DSE UBI images to matrix (#249) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/5799a8f24ef93d77000054c5c1246fc5e8d3eb1e)
*  Wait for Ubuntu based DSE image to be published before UBI image [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/191028b2fbbeeb285e298c6d94d12bad773442b5)


## v0.1.53 (2023-01-17)

*  Unescape unicode characters in the system_distributed_replication properties (#244) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a136b2e2a0da3b405aa570cde0439415303521e6)
*  New metrics collector implementation (#238) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f631db71b4f4bfefb3791e42b080e6c2df3f7fc5)
*  Use io.dropwizard.metrics version from Cassandra 4.x (provided) and change netty to provided (#245) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/205aeca5253166bcf892d963e582f23142e50b23)
*  Add CDC Agnet to DSE images (#243) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b5fe1b579369e32fcbc5cdc89ad8fbac321317ee)
*  Remove DSE UBI images from the matrix [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/6004959cc54f3222184b5226ff5048c3d28f6a26)


## v0.1.52 (2022-12-14)

*  Add GH Projects automation (#227) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/8f573cc750de1e2e8ad9c2db7c5b7277739ac5bd)
*  Update Cassandra 4.1 version (fixes #232) (#235) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/d3827cee5e9402dd835debfd8d2e7e3c51cef075)
*  Update DSE version (#237) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/22ba959095ba4e5912c29df22a6c6ff3c70af86b)


## v0.1.51 (2022-11-15)

*  Add DSE 6.8.29 (#226) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/88935552c1a2ea9b3134c383bab279cf6f2722d8)


## v0.1.50 (2022-11-15)

*  Implement endpoint for nodetool move (#221) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/7876862f4eaf8f3dfe764d75bbbc6b2fe87d5cbb)
*  Add jdk8 tags for DSE images (#220) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b6341fcf8460654728c39b5c98c5fa345a7abf59)
*  Fix failing test due to race condition (#222) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/d2aee7c1b974ef33038c650933ab29033a51d27a)
*  Fix failing test due to incorrect assertion (#225) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/509b280c45a481d3d38042d841a19bd2e41143d0)
*  Update swagger document [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/44495f1a190548ae163220d04a66d05b8c29729b)


## v0.1.49 (2022-10-28)

*  Update Cassandra and DSE versions (#218) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/726db0b8ebfd2f0d6a8b082d62c6f6e4e2998a88)


## v0.1.48 (2022-10-14)

*  Add missing v1 Table resource endpoints (#213) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/edf0c28da4a01511661e4648570e76a1d0ea4995)
*  Add Cassandra 4.1 builds (#183) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/5001edfdea5c649103380e68d50447c655238669)


## v0.1.47 (2022-09-23)

*  Fix DSE image releaser [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/d94ac7940420106a859640dffb6e5aedad27e691)


## v0.1.46 (2022-09-23)

*  Update DSE image builds based on docker-images (#208) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/5b3a5d860cdcdaa6eacfc3630175b16ce9eb8823)


## v0.1.45 (2022-08-29)

*  Add async versions of scrub, compactions and upgradesstables (#204) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a8e419f16de40db187d0e10003a0e6dc4afcecb4)
*  Update to DSE 6.8.25 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a0f7c9b5460a3db9c31920a99a7364da06185781)
*  update test to build DSE images with buildx [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/74748c0a128a4e9f8231f0213211180e1b50b65c)
*  Update DSE release and README [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/6c8e7e069b29b916f35997bf83832c58060a641d)
*  Add Cassandra 4.0.6 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/d03f6b72c51ac9c63bce4d0f52a3b9b771edddd7)


## v0.1.44 (2022-07-21)

*  Add Cassandra 4.0.5 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/9f7183a735ca218b6ca897be3bec1e89fd62c259)


## v0.1.43 (2022-07-05)

*  Bump logback-core from 1.2.3 to 1.2.9 in /management-api-server [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/9cb930951bd889f051cfdd415f57dc6737ca9d2f)
*  Bump logback-core from 1.2.3 to 1.2.9 in /management-api-common [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/1415364784452dcfedc85080144823bf6206cc62)
*  Moving CDC agent to /opt/cdc_agent/cdc-agent.jar for all agent versions and all C*/DSE versions. [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/c82941927fdf0c997a0689bc5115ac876bac3b0a)


## v0.1.42 (2022-06-15)

*  Apache Cassandra 3.0.x, 4.0.x images updated to include CDC agent for Apache Cassandra. [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/110e8a298272c318dd78b32307c7f74224295680)
*  CDC agent added to DSE image. [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/807dbbad46e7dc9ba0e6e12143a1305afc5580eb)
*  Fix CDC agent download (missing `v` was throwing a spanner in the works). [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/abdc700d866fbf441697568148492cece90781a9)
*  Still had the wrong URL path. Consistency isn't great here. [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/1995379c0dc5f2311c46f469cf680d0b66fda4c1)
*  CDC agent version bump to 2.0.0. [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/6d67e3985792226b5e06c1f51d0b5b4a8b1f70d9)
*  Use --chown for COPY commands to avoid duplicating layers [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/7b376ea7beb4203880652456f556fb63c295b429)


## v0.1.41 (2022-06-07)

*  Fix nodetool wrapper [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/c5d55f9b2678d5a4647b8c391b701c2550f5a6e8)


## v0.1.40 (2022-06-02)

*  Add nodetool fix for JDK-8278972 (CASSANDRA-17581) (#196) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a7e16a3496340e40690a95d259e501ac841c9e52)


## v0.1.39 (2022-06-01)

*  Add C* 3.11.12 and 4.0.3 back to the image matrix (#194) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/73fe9ac1ccad115297ee8a4712da1800c7607a39)
*  Use a build matrix for Docker releases (#195) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ad92c5755d38f3bb464f838d482a7f871d5c167a)


## v0.1.38 (2022-06-01)

*  add mtab symlink (#182) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ed0fd216add963dbb2ecbec42f9c4e2d67e261d1)
*  Disable DSE builds when Artifactory secrets are not available (#186) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/992f543546d4643f0024fb3e6ea93a498575cc01)
*  Switch to using archive.apache instead of downloads.apache for Cassandra 3 tests. [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/7793116106fd2998544bc2f7e2539b3b9b3e1e65)
*  Upgrade to Cassandra 3.11.13/4.0.4 (#191) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b52908ea75f8b95bf4641d21e297215252401774)


## v0.1.37 (2022-02-17)

*  Upgrade Cassandra versions to 3.11.12/4.0.3 (#177) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a8feb80266e076b13a5eeff93d8a5c309a8352df)


## v0.1.36 (2022-02-03)

*  Prevent driver session initialization if socket file does not exist (#175) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/15e98761f283af06b71186dbd9a755154db13219)


## v0.1.35 (2022-01-19)

*  add endpoint to list schema versions, needed to check schema agreement (#172) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/7acc28c87db2c9979ab7a94df452082fd612440f)
*  Improve test speed [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ffcd0ef7da03b1b5218a8cb42e3c03b11496e68c)


## v0.1.34 (2022-01-04)

*  add @Rpc annotation (#166) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/df554e2fd7c697e2192306616e750077dbc76d16)
*  Update to latest version of Fossa CLI (#167) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f3a949a7f3d95bedcce7c558e196e7d2a12a094b)
*  Add OpenAPI doc generation [K8SSAND-967] (#164) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f20b4691d2dc81eecd8e2f796ca9b1ab1f5d660f)


## v0.1.33 (2021-11-12)

*  Publish Docker images after PR merges (#158) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b5c0341c1c1088f55512cefe44fd06f9051a82fa)
*  Add endpoint for rebuild (#161) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/dd2fafbcee0106c0f11add2bc5b31de121661b9d)
*  Add KQueue channel creation for MacOS [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0ec69fe44bd96b3a96f342c3f550b7ab64d62882)
*  Remove README bit about tests failing on MacOS [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/bbe569dc6492d9412cda1dd8c085d4527b9b019c)
*  Fix NPE in CallStatus and change return code to 404 if job does not exists (#162) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/cce156f35fae6c42e585d5ba1505a8cf1357be36)


## v0.1.32 (2021-10-25)

*  Write metric filters provided as an env variable to the mcac config file. [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/1eb99aa34fbeae50803d9d0482448247f0e532b2)
*  Move async operations of cleanup and decommission to api/v1 (#154) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/c0a5482778866fca0c71674a0e93d374493a3049)


## v0.1.31 (2021-10-18)

*  * Add full_query_logging to featureSet [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ebb5c0e71eda1c74abf3d1b3d5b1c28d4d7efa75)


## v0.1.30 (2021-10-13)

*  Add missing Swagger docs for nodetool repair and list keyspaces [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/3345e284e92fa874a8b4b61a7cb202ac67fccfe6)
*  IDE cruft added to .gitignore. [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/81687568abf0e218cdcbaed71dc8d07188401a35)
*  * Add full query logging endpoints to server, methods to CassandraAPI and implementations thereof, RPC methods to NodeOpsProvider. [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f96a657abcf93554ce24eaef2cc8a568d493395c)
*  * Add 'adding features' section to README. * Add note to README that tests fail on MacOS. [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/bc264c8e28caa6522a649f1af68d738ff5ef75a4)
*  Fix FQL Swagger docs [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/cf91d3c91e68d4595262aeb9f4efda1f55da0bf9)
*  Bump tini ver [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/868baa88c041489fd82bcc8489dda515d99efa34)
*  Add endpoints to create and list tables; and to retrieve keyspace replication settings (#143) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/3c18b9c7d6944d27870950adecc283b6da70a0e1)
*  Use internal form for CQL identifiers (#146) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f0920440581fe57088f17bc1757e084464ed905b)
*  Execute ssTableTasks in the background, provide FeatureSet endpoint [K8SSAND-948] (#141) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/95bc9f72de5b3a088494a190d706d66d422d819c)
*  make sure the MCAC base directory exists [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/4da6bb545454790c8cbe1f325f72d25cde10ec9a)
*  Use default heap of 128m [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/786819107e7b1d2fbcc7b50755f7a37278257342)


## v0.1.29 (2021-09-16)

*  Add operation to list keyspaces (#127) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/e77aa6ab32fb119fbb865cf8edc3f8a9a37eab95)
*  Add per dc replication factor overrides [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/69da814a1da64fe6b9d9f587f559c885d3408caf)
*  Fix dc replication test [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/9ff3f8e77790a74a25c894a2d1fbf1a26c3a79da)
*  Add suuport for running repairs [K8SSAND-154] [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/5a13ce6c16d4a690bfb4dc59d3addb8f8d797e19)
*  Add Cassandra 4.0.1 and updated docs [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a258950de01a765acd22f49ec025208bb986d0e0)


## v0.1.28 (2021-07-29)

*  Add Cassandra 3.11.11 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/e77fe652e9e1d36c18d83e2aec9987e3ce149d62)
*  Make heap memory configurable [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/462ef80d808f5fb0eac101234e91fec467f4912d)


## v0.1.27 (2021-07-26)

*  Upgrade to 4.0.0 GA [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/6a4caa5d775de7465e9927be1727600306546105)
*  Change install directory for Fossa CLI in license-check workflow [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/3b36a0fb546795537906d34a7abdb9c43a143a6e)
*  Cassandra is &reg; not &trade; per ASF https://www.apache.org/foundation/marks/list/ [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/8f2a674c5930617b74d466e8345b44767e2fb9c5)


## v0.1.26 (2021-07-01)

*  Update secrets for pushing DSE images [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/1c078acd0df7d29c91f20bc105cce5e9314af019)
*  Remove bintray [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/6caa02bb90d51147d5e213fa4d92be9c38acce40)
*  Update default C* 3.11 image to 3.11.10 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/bbd8e3289bf51ff3bdf66925ac8a6192614e08c5)
*  Update Guava dependency (#110) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/31c2b48c79a315071b11d4624e91ec68a576b145)
*  Fossa Integration (#111) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f2fdb336d775b37088275f78994d7834a7f1d15e)
*  Fix --cassandra-home in entrypoint script (#112) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/56179fea4376b748665268040b9b7fd651963669)
*  Bump to latest RC2 release of C* (#115) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b5ec473fb0067e24fe66775b1b181d786cffc23a)


## v0.1.25 (2021-04-27)

*  Enable ARM64 builds for Cassandra [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/736019f4ebc413002f490223257ccb454978fe06)
*  Update to Cassandra 4.0-RC1 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/d82d54de166127f662e4fa5a1ff2708da2536dcb)


## v0.1.24 (2021-04-08)

*  dse-db support for api-agent-4.x  (#92) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/df33bd68a06042bf28850d0b45f51b24fbb5d373)
*  Relocate Management API to K8ssandra org [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0d0c5200c7bfaa21b2f6992a4eaa9432a7166163)
*  Move publishing Docker images to K8ssandra org [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a317ba40fae8e2b11eecde01ad5e6ee15aebdb0f)


## v0.1.23 (2021-03-03)

*  Create home directory for cassandra user (#89) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/858e0e78d04e5c56335eda89b6851b2411ae8eb9)
*  Force protocol v4 for driver connection [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/62b975f212dd483eadb87c09a9623bca1a14601d)


## v0.1.22 (2021-02-17)

*  Speed up CI tests [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/9441327a40dfbffb820864fd2d84f6e0a18264ae)
*  Update RestEasy libs to address CVEs [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f31efb7adc3525190e14c315815f6cfdbee4b074)


## v0.1.21 (2021-02-12)

*  Update to MCAC v0.2.0 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/d6a12bf80839a132e9b0cf4a1c9ccbb14112826b)


## v0.1.20 (2020-12-10)

*  Update for CASSANDRA-15299 protocol v5 changes [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0608d0c32e8f7d1bb51e5d9b62e05eb767474a4f)


## v0.1.19 (2021-02-03)

*  fix docker release for 3.10.11 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0cdeb6512ba3bd86c8fd717c2da49ef11bca55c8)


## v0.1.18 (2021-02-03)

*  fix docker publish commands [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f3223a6297ab546471cc2332d3657a96bbcd0915)
*  fix docker publish commands [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/52800a1b493d8de66b97b654901a650bad52e1da)
*  add Cassandra 3.11.10 image [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0f476114fd4427692f3dca3dfd248f2b6b69c45a)


## v0.1.17 (2020-12-21)

*  fix docker publish commands [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f3223a6297ab546471cc2332d3657a96bbcd0915)


## v0.1.16 (2020-12-21)

*  Add Docker image generation for newer Cassandra versions [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/fe3825b75bb689d4015ab9a306df31aad11ad987)


## v0.1.15 (2020-12-10)

*  do not copy jars to /etc/cassandra to avoid be shadowed by volume mounts [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/2b423002085e542d535720ce7bfbe11ba52d21bf)
*  Update setup-java github action [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/c201856ff76e24771bdb40a2794ad3b618a60477)
*  Do not copy jars to /etc/cassandra to avoid being shadowed by volume mounts [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/29170b8b7c071e2452f1e70558132259633f9161)
*  Split multi-platform build step [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/7da4fed741ba5a5c0cff30c1abf2b7c1f748a648)
*  Update buildx ghaction [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/05c566ce6987b033e8385b731af37278bb787b7e)


## v0.1.14 (2020-11-18)

*  Use HintsService in place of HintedHandoffManager (#54) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/56d0ec0725be8e6c1f537f0c852e4afa02bcb460)
*  Add snapshot management (#53) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a3d271c1592df1943106c78e2802bd18f8c2fb82)


## v0.1.13 (2020-08-26)

*  Experimental arm64 support for Cassandra 3.11.6  (#36) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/52564256ff3f692d24a494715a0815ac0004c5a3)


## v0.1.12 (2020-08-19)

*  Bump to Cassandra 3.11.7 (#39) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/5b98bcc39b54eb5f80a4a9edf6adb7dcd83fdb11)


## v0.1.11 (2020-07-22)

*  Redirect stderr to stdout (#33) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/10b21c3b6620fb89e231642676fd9663dcdf6a4f)


## v0.1.10 (2020-07-21)

*  Add new intercept for QueryHandler.parse which is new in 4.0 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/3efdc84c4786230ab128e00420108b3eb69879d8)
*  Fix build [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/79c1cc4a7c163b97929a8e95acee36ea7f3ef214)


## v0.1.9 (2020-07-21)

*  Add 4.0-beta1 to temp docker img [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/84e691d8c5b108079a0c0ef83ca31e3a65c3ca5e)


## v0.1.8 (2020-07-06)

*  Add API endpoint that allows creating a Keyspace [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/2d289d81e623211fdebb521b6df74113ac0c6266)
*  Add API endpoint that returns the local datacenter [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b82d6e09d5f698fee908def2782fb288c265be21)
*  Retrieve local datacenter when creating a keyspace [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/68c5e9658ec888c2321642b989b220db7b4b2ec2)
*  Make sure replication factor overrides are applied to system_auth / system_distributed / system_traces keyspaces [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0f1dc2ead15c5993e71d4ac4170c995bbd41cb6f)
*  Verify RF is being set correctly for system_auth / system_distributed / system_traces keyspaces [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/7dace8050a48214c0d83e6e70af398ce8c51faca)
*  Add API endpoint that allows altering the RF settings of a Keyspace [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/5bd71711959402fee247ee94f61b76af8b215492)
*  Update openapi docs [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/38448c15d344c77df72a460c5baefa42210c8f78)


## v0.1.7 (2020-06-25)

*  Fix build [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/fdf07f294fac4e4f9588e05ddd45a881b32a959b)


## v0.1.6 (2020-06-25)

*  CDB-39: Add DSE support to the Management API [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/db92a3db830f5ff6eb324ee6b27a4efe35b09602)
*  CDB-39: Show tail of system.log when a test fails [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/27db87f80d9722fff46e62dab27dd5a578ba8e91)
*  CDB-39: Fix truncating with Host test [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/5bb6033c4f75ae85298c0975a6403e6d20821dea)
*  CDB-39: Add DSE Docker files for local testing [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/96a9e474e490bd8a19ac8976b9e147ba9ac3990a)
*  CDB-39: Move access to StorageService/RoleManager/CompactionManager/Gossiper into CassandraAPIs [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/2a192c0f183fa10fa26cc06d95508c20aec4842f)
*  CDB-39: Fix RPC Calls for DSE [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/de8f0cb6f7ab568e608f68c3be38749d4eeea4be)
*  Fix tests, add dse tests to ci [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/207964548ca3c78df2b5d10a279e8992d9428b4a)
*  Change default image, fix snapshot url [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/009a38ecf7efc0ee55a2b8d549a7e95734a70787)
*  Fix dockerfile for dse [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a5aadf2288b772ffe01b18e0430b5e1d9b704b24)
*  More repo permission issues [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0e2b330c0f9274b34f042d48cd967100cd3f6fa2)
*  Increase timeout [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/26b50897de63aae47d1a140111b85d4d2551cb02)
*  CDB-39: Use Awaitility to check until a condition is being met [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/bbeb3b62393ae0fb5945a997797452e8847e6f93)
*  CDB-39: Truncating with host requires localhost with DSE [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0179a4cdc9835afb4b2e093dee707d3b318f3d6a)
*  CDB-39: Make sure ip command is available [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0c9fe449e54a0d08988559442d33e12d8227a6ff)
*  CDB-39: Correctly set native_transport_address / native_transport_broadcast_address for DSE [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/0753dfce85f6e0514e7489433c4bce429060c1a4)
*  CDB-39: Add parameter name to parameterized tests [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a43ac5f4f97db9cae282aec3bb5d302a025f4aa7)
*  Add dse artifacts to other GH actions [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ff38d50bab1a4a6ef9aba0c57572a48ba7d439fa)
*  Fix test errors in docker from maven [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b849a5a54cdc43fc1eff7459db9b0db145d15129)
*  Cleanup stale unix socket [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/2852f48444eab066974e8fec56044417f2477296)
*  Add maven releases (#25) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a7c1260e486283c388b08b95c6545970c7af3c03)


## v0.1.5 (2020-06-19)

*  Support USE_MGMT_API env var (#23) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/e22a33c63784c13a8ed0c3bef7cafe13fa80be0a)


## v0.1.4 (2020-06-10)

*  CDB-34 Added Docker files and scripts to make the management API work with Astra-flavoured OSS distributions. (#15) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/1a225f99f91863673b074b3c1a9c1d2d9cd79ecd)
*  Fixes #18 filter out tcp nodeInfo when connected over unix-socket (#19) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/44d35830c2adcf0ee531a4576cd6c4475c94a2bc)
*  Reduce the threads in a few places (#17) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/e73ed3e4fef81d3b1b72064edddeb8fd7ab4b30f)
*  Update 4.0 docker image to 4.0-alpha4 [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f4d4c8054e3c46f0d5018b64a251be339e792858)


## v0.1.3 (2020-05-20)

*  /configure accepts json as well as yaml; JAVA_OPTS in docker-entrypoint.sh; some error message cleanup for /configure [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/f52f23831e64993dff81b8dcc37393c1ba0b26b9)
*  fix != started bug [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/11ba7035b54ade2573b2c7965353e7fa0de72470)
*  Update docker-entrypoint.sh [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b86fbea393ac76830c1dcdc0944a3fb2e075caaa)
*  Add endpoint that allows access to streaming info (#14) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/a2a6397ad5400219720ce74885b6152218b86944)
*  update bytebuddy to support jdk14 (#16) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/41ea2db23ac3cf59367ebf54bc6ff78aa5dfa024)


## v0.1.2 (2020-05-08)

*  Add wget to Cassandra images (#13) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/8b996a8ebe58c7eb2ca78504c30717f582fe9007)
*  Fix 4.0 dockerfile [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/ce146ec40cb4c2abf79b3f259a47a570f1a29292)


## v0.1.1 (2020-05-08)

*  Update README.md [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/3ddd3d95d07264af46c85362627e5e83b480eca3)
*  Improve README / Don't print usage errors when using -h/--help  (#9) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/9c25b8118f6b9227c3ffc6218bf3327f0690c1f6)
*  jar-release [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/5dccf308b0abd7876eb29879b3c0fda844cdafd7)
*  clone [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/6b44c5f0be7eceaf3824e30a1a68e36da96d06be)
*  Update jar-release.yaml [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/da96993b760d076d6c665898cf3c7cdd1f257723)
*  Update jar-release.yaml [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/43f46e210bbc5a91824844221ccedefcc35709a8)
*  Update jar-release.yaml [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/b5c01a4edfce43d1c7d4328c3aca8b63c988cddc)
*  Update jar-release.yaml [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/aec1d86c2769d489781395847057e01002f1c855)
*  update [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/6374cf9f0842a78f7c92d10d88d15ac7734effd3)
*  action [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/3ecfe53869f5ad0c7f944138f993534b77dba883)
*  jars.zip jar release [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/4e42888a2c207a10dd632b74e00e466fe73c837c)
*  README [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/8b70fa5f2a36bc298ae912d98d5923dbba2b395a)
*  Minor improvements to the README (#11) [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/6adf8add0d4cff72da64e035970d43ffe46ab124)
*  Add MCAC to images [View](https://github.com/k8ssandra/management-api-for-apache-cassandra/commit/bbadc4952dc0ec62ac2321fba417e8da0be74e35)


## v0.1.0 (2020-03-27)



