# core3
Scala framework for building web applications and services based on [Play](https://www.playframework.com/) and [Akka](http://akka.io/).

## Motivation
* Minimizes effort required to secure web applications
* Separates actions (as workflows) and data objects (as containers) into easily testable and maintainable units
* Disconnects application logic from data layer and action management

## Getting Started
1. **Add dependency**
```
libraryDependencies += "com.interelgroup" %% "core3" % "1.0.0"
```

> See 'Additional dependencies' for other libraries you may need

2. **Define containers**

3. **Define workflows**

4. **Create application**
    1. Create backend service ([Example Engine](https://github.com/Interel-Group/core3-example-engine))
    2. Create frontend service ([Example UI](https://github.com/Interel-Group/core3-example-ui))

    or

    1. Create one frontend service that includes the workflow engine component

For more information, check the wiki and the example projects:

* [Wiki](https://github.com/Interel-Group/core3/wiki) - Docs
* [Example Engine](https://github.com/Interel-Group/core3-example-engine) - Example backend web service
* [Example UI](https://github.com/Interel-Group/core3-example-ui) - Example user interface application, utilizing the example backend service

## Components
* Config - TODO
* Core  - TODO
* Database - TODO
* HTTP - TODO
* Mail - TODO
* Security - TODO
* Test - TODO
* Utils - TODO
* Workflows - TODO
* Services Setup - TODO

## Supported data sources
* [MariaDB](https://mariadb.org/) (tested on 10.0)
* [CouchDB](http://couchdb.apache.org/) (tested on 1.6.0)
* [Elasticsearch](https://www.elastic.co/products/elasticsearch) (tested on 5.1.2)
* [Redis](https://redis.io/) (tested on 3.2.5)
* [Solr](http://lucene.apache.org/solr/) (tested on 6.3.0)
* [DistributedCache](https://github.com/Interel-Group/core3/wiki) (uses another source for persistence)
* [MemoryOnly](https://github.com/Interel-Group/core3/wiki) (offers no persistence)

## Supported auth providers
* [Auth0](https://auth0.com/) - JWT based authentication and authorization
* [Local](https://github.com/Interel-Group/core3/wiki) - local credentials DB

## Additional dependencies
Depending on your data layer setup, you may have to include one or more of the following dependencies:

###### MariaDB
```
libraryDependencies += "org.mariadb.jdbc" % "mariadb-java-client" % "1.5.9"
```

###### Redis
```
libraryDependencies += "com.github.etaty" %% "rediscala" % "1.8.0"
```

###### Elasticsearch
```
libraryDependencies += "com.sksamuel.elastic4s" %% "elastic4s-core" % "5.1.4"
```

###### DistributedCache
```
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % "2.4.17",
  "com.typesafe.akka" %% "akka-cluster-tools" % "2.4.17",
  "com.github.blemale" %% "scaffeine" % "2.0.0"
)
```

###### E-mail support
```
resolvers += "lightshed-maven" at "http://dl.bintray.com/content/lightshed/maven"
libraryDependencies += "ch.lightshed" %% "courier" % "0.1.4"
```

###### CLI support
```
libraryDependencies ++= Seq(
  "org.jline" % "jline" % "3.2.0",
  "com.github.scopt" %% "scopt" % "3.5.0"
)
```

> CouchDB and Solr require no additional libraries

## Testing
Running the supplied tests will require all supported services to be present and active on the test machine.

### Test configuration
The default test configuration can be found [here](src/test/resources/static.conf).

For security, the various usernames and passwords have not been included and will need to be supplied either in the test `static.conf` file or via `-D` JVM options (preferred).

### Unit tests
Main test suite for verifying core component functionality.

```
sbt "testOnly core3.test.specs.unit.*"
```

Required options:
```
-Dserver.static.database.mariadb.username=<some user>
-Dserver.static.database.mariadb.password=<some password>
-Dserver.static.database.solr.username=<some user>
-Dserver.static.database.solr.password=<some password>
-Dserver.static.database.redis.secret=<some password>
-Dserver.static.database.couchdb.username=<some user>
-Dserver.static.database.couchdb.password=<some password>
```

### Property tests
Additional tests for data-centric components.

```
sbt "testOnly core3.test.specs.prop.*"
```
Required options:
```
-Dserver.static.database.mariadb.username=<some user>
-Dserver.static.database.mariadb.password=<some password>
-Dserver.static.database.solr.username=<some user>
-Dserver.static.database.solr.password=<some password>
-Dserver.static.database.redis.secret=<some password>
-Dserver.static.database.couchdb.username=<some user>
-Dserver.static.database.couchdb.password=<some password>
```

### Performance tests
Additional tests for tracking performance changes over time.

> It's best to avoid, as this test suite is not entirely stable and has issues dealing with async operations

```
sbt "testOnly core3.test.specs.perf.*"
```
Required options:
```
-Dserver.static.database.mariadb.username=<some user>
-Dserver.static.database.mariadb.password=<some password>
-Dserver.static.database.solr.username=<some user>
-Dserver.static.database.solr.password=<some password>
-Dserver.static.database.redis.secret=<some password>
-Dserver.static.database.couchdb.username=<some user>
-Dserver.static.database.couchdb.password=<some password>
```

### Multi-JVM tests
Multi-JVM tests for the `core3.database.dals.memory.DistributedCache` data layer.

```
sbt "multi-jvm:test-only core3_multi_jvm.test.specs.DistributedCache  -- -Dserver.static.database.mariadb.username=<some user> -Dserver.static.database.mariadb.password=<some password>"
```

## Built With
* Scala 2.11.8
* sbt 0.13.13
* [Play 2.5.x](https://github.com/playframework/playframework) - Base framework
* [Akka 2.4.x](https://github.com/akka/akka) - Core component actors and (optional) data layer clustering
* [Slick](https://github.com/slick/slick) - SQL data layer support
* [rediscala](https://github.com/etaty/rediscala) - (optional) Redis data layer support
* [elastic4s](https://github.com/sksamuel/elastic4s) - (optional) Elasticsearch data layer support
* [courier](https://github.com/softprops/courier) - (optional) E-mail support

## Versioning
We use [SemVer](http://semver.org/) for versioning.

## Future Goals
- [ ] Play 2.6 and Scala 2.12 support
- [ ] Improve test coverage
- [ ] Improve performance testing
- [ ] Generate container boilerplate and data conversions with macros (and/or `scala.meta`)
- [ ] Make `slick` dependency optional
- [ ] Add / verify support for additional (No)SQL DBs

## License
This project is licensed under the Apache License, Version 2.0 - see the [LICENSE](LICENSE) file for details

> Copyright 2017 Interel
>
> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this file except in compliance with the License.
> You may obtain a copy of the License at
>
> http://www.apache.org/licenses/LICENSE-2.0
>
> Unless required by applicable law or agreed to in writing, software
> distributed under the License is distributed on an "AS IS" BASIS,
> WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
> See the License for the specific language governing permissions and
> limitations under the License.
