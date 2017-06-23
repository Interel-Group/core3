# core3
Scala framework for building web applications and services based on [Play](https://www.playframework.com/) and [Akka](http://akka.io/).

## Motivation
* Minimizes effort required to secure web applications
* Separates actions (as workflows) and data objects (as containers) into easily testable and maintainable units
* Disconnects application logic from data layer and action management

## Getting Started
```
libraryDependencies += "com.interelgroup" %% "core3" % "2.0.0"
```

> See [Additional dependencies](https://github.com/Interel-Group/core3/wiki/Additional-Dependencies) for other libraries you may need.

For more information, check the wiki and the example projects:

* [Getting Started](https://github.com/Interel-Group/core3/wiki/Getting-Started)
* [Wiki](https://github.com/Interel-Group/core3/wiki)
* [Example Engine](https://github.com/Interel-Group/core3-example-engine) - Example backend web service
* [Example UI](https://github.com/Interel-Group/core3-example-ui) - Example user interface application, utilizing the example backend service

## Supported data sources
* [MariaDB](https://mariadb.org/) (tested on `10.0`, `10.1`)
* [CouchDB](http://couchdb.apache.org/) (tested on `1.6.0`, `2.0.0`)
* [Elasticsearch](https://www.elastic.co/products/elasticsearch) (tested on `5.1.2`, `5.3.1`, `5.4.0`)
* [Redis](https://redis.io/) (tested on `3.2.5`, `3.2.8`)
* [Solr](http://lucene.apache.org/solr/) (tested on `6.3.0`, `6.5.1`)
* [DistributedCache](https://github.com/Interel-Group/core3/wiki/DistributedCache) (uses another source for persistence)
* [MemoryOnly](https://github.com/Interel-Group/core3/wiki/Databases#memoryonly) (offers no persistence)

## Supported auth providers
* [Auth0](https://auth0.com/) - JWT based authentication and authorization
* [Local](https://github.com/Interel-Group/core3/wiki/Controllers-(Basics)) - local credentials DB

## Additional dependencies
Depending on your data layer setup, you may have to include one or more of the dependencies listed [here](https://github.com/Interel-Group/core3/wiki/Additional-Dependencies).

> CouchDB and Solr require no additional libraries

## Testing
Running the supplied tests will require all supported services to be present and active on the test machine.

### Test configuration
The default test configuration can be found [here](src/test/resources/static.conf).

For security, the various usernames and passwords have not been included and will need to be supplied either in the test `static.conf` file or via `-D` JVM options (preferred).

> For MariaDB, a test database needs to be created and it needs to use UTF8:
>
> ```
> CREATE DATABASE core3_test CHARACTER SET utf8 COLLATE utf8_general_ci;
> ```

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

### Scalameta tests
```
sbt "project meta" "testOnly core3.test.specs.unit.meta.*"
```

## Built With
* Scala 2.11.11
* sbt 0.13.13
* [Play 2.5.x](https://github.com/playframework/playframework) - Base framework
* [Akka 2.4.x](https://github.com/akka/akka) - Core component actors and (optional) data layer clustering
* [Slick](https://github.com/slick/slick) - SQL data layer support
* [Scalameta](https://github.com/scalameta/scalameta) - Macro annotations support
* [rediscala](https://github.com/etaty/rediscala) - (optional) Redis data layer support
* [elastic4s](https://github.com/sksamuel/elastic4s) - (optional) Elasticsearch data layer support
* [courier](https://github.com/softprops/courier) - (optional) E-mail support

## Versioning
We use [SemVer](http://semver.org/) for versioning.

## Future Goals
- [ ] Play 2.6 and Scala 2.12 support
- [ ] Improve test coverage
- [ ] Improve performance testing
- [x] Generate container boilerplate and data conversions with macros (and/or `scala.meta`)
- [ ] __Make slick dependency optional__
- [x] Add / verify support for additional (No)SQL DBs (now supports all Slick databases)

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
