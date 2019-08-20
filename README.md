# akka-persistence-maprdb

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.anicolaspp/akka-persistence-maprdb_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.anicolaspp/akka-persistence-maprdb_2.12) [![Build Status](https://travis-ci.com/anicolaspp/akka-persistence-maprdb.svg?branch=master)](https://travis-ci.com/anicolaspp/akka-persistence-maprdb)

This is a plugin for Akka Persistence that uses MapR-DB as backend. It implements a Journal store for saving the corresponding events for persistence entities and a Snapshot store.

- [Linking](https://github.com/anicolaspp/akka-persistence-maprdb#linking)
- [Plugin Activation](https://github.com/anicolaspp/akka-persistence-maprdb#activation)
- [MapR-DB Configuration](https://github.com/anicolaspp/akka-persistence-maprdb#mapr-db-configuration)
- [MapR Client](https://github.com/anicolaspp/akka-persistence-maprdb#mapr-client)
- [How is data stored in MapR-DB](https://github.com/anicolaspp/akka-persistence-maprdb#how-is-data-storey-in-mapr-db)
- [Journal Tests](https://github.com/anicolaspp/akka-persistence-maprdb#journal-tests)
  - [Test Output](https://github.com/anicolaspp/akka-persistence-maprdb#tests-output)
- [Persistence Query Side](https://github.com/anicolaspp/akka-persistence-maprdb#query-side)


### Linking

Releases are pushed to Maven Central.

```xml
<dependency>
  <groupId>com.github.anicolaspp</groupId>
  <artifactId>akka-persistence-maprdb_2.12</artifactId>
  <version>1.0.2</version>
</dependency>
```

```scala
libraryDependencies += "com.github.anicolaspp" % "akka-persistence-maprdb_2.12" % "1.0.2"
```

### Activation

```
akka {
  extensions = [akka.persistence.Persistence]

  # This enables akka-persistence-maprdb plugin
  persistence {
    journal.plugin = "akka-persistence-maprdb.journal"
    snapshot-store.plugin = "akka-persistence-maprdb.snapshot"
  }
}
```

### MapR-DB Configuration

TWe need some settings to be set up for MapR-DB. 

- `maprdb.path` is the base MFS path where our journals and snapshots live.
- `maprdb.pollingIntervalms` is used by the query side for polling the new persistence entity ids. 
- `maprb.driver.url` can be used to configure what kind of MapR-DB implementation we want to use. `ojai:mapr:` points to the real MapR cluster. However, we could use an in-memory implementation through [ojai-testing](https://github.com/anicolaspp/ojai-testing) by indicating `maprdb.driver.url = ojai:anicolaspp:mem`. Notice that we package `ojai-testing` within `akka-persistence-maprdb` but it should not be used in production.  

The following configuration shows that our journals and snapshots will live inside the folder `/user/mapr/tables/akka` in the MapR File System. We can indicate any valid location in the distributed file system within MFS. The polling interval is `5` seconds and we use a real MapR cluster throught the MapR client and driver. For reference about OJAI, please see the related [MapR Documentation](https://mapr.com/docs/61/MapR-DB/JSON_DB/develop-apps-jsonDB.html)

```
maprdb {
  path = "/user/mapr/tables/akka"
  
  pollingIntervalms = 5000
  
  driver {
    url = "ojai:mapr:"
  }
}
```

For each persistence entity a MapR-DB tables will be created. For example, if we have the persistence entity `user` then two tables are automatically created.

```
/user/mapr/tables/akka/user.journal
/user/mapr/tables/akka/user.snapshot
```
These two tables are created automatically the first time the plugin is activated, after that they will consecuentenly be used to read the initial state of the persistence entity when needed and to save new events and snapshots.

### Persistence Entity Ids Table

one additional MapR-DB table is created along with your journals and snapshot. The table will have the following name:

```
/user/mapr/tables/akka/ids
```

Notice that the base path is what we indicated in the configuration. The table name is `ids`. This table is set of all `persistence entity ids` that is use in the query side. There are different ways to queries the `persistence entity ids`. One possible way is to optain a handler to the MapR distributed file system (MFS) and enumerate the tables there. However, having an extra table (`ids`) makes all very easy.

### MapR Client

`akka-persistence-maprdb` plugin uses [OJAI](https://mapr.com/docs/61/MapR-DB/JSON_DB/UsingJavaOJAI.html) and the MapR Client to communicate with the MapR Cluster. Make sure you have configured the MapR Client accordingly. In a secured cluster, make sure that the corresponding `mapr ticket` has been created so authentication happens correctly. 

### How is data storey in MapR-DB?

`akka-persistence-maprdb` plugin uses MapR-DB JSON to store the corresponding user defined events and persistence entity snapshots into MapR-DB. 

As mentioned above, each `.journal` table contains the events for the corresponding persistent entity and the following structure is used. 

```
{
  "_id": {"$binary": sequenceNr in binary format},
  "persistentRepr": {"$binary": persistentRepr in binary format},
  "deleted": {"$boolean": true or false}
}
```

Each row is an even and they are sorted by MapR-DB based on the `_id` in `ASC` order.

Each `.snapshot` table represents the snapshots taken for an especific persistent entity and the following structure is used. 

```
{
  "_id": "persistenceId_sequenceNr_timestamp", // this is String sorted ASC by default
  "meta": {
    "persistenceId": {"$long": persistenceId},
    "sequenceNr": {"$long": sequenceNr},
    "timestamp": timestamp 
  },
  "snapshot": {"$binary": binary representation of the Snapshot class}
}
```

## Journal Tests 

All tests for the journal passed. However, since we don't have a MapR Cluster in Travis we are going to ignore the 
the test. One can run the test locally against a configured MapR Cluster

### Tests Output

```
[info] MyJournalSpec:
[info] A journal
[info] - must replay all messages
[info] - must replay messages using a lower sequence number bound
[info] - must replay messages using an upper sequence number bound
[info] - must replay messages using a count limit
[info] - must replay messages using a lower and upper sequence number bound
[info] - must replay messages using a lower and upper sequence number bound and a count limit
[info] - must replay a single if lower sequence number bound equals upper sequence number bound
[info] - must replay a single message if count limit equals 1
[info] - must not replay messages if count limit equals 0
[info] - must not replay messages if lower  sequence number bound is greater than upper sequence number bound
[info] - must not replay messages if the persistent actor has not yet written messages
[info] - must not replay permanently deleted messages (range deletion)
[info] - must not reset highestSequenceNr after message deletion
[info] - must not reset highestSequenceNr after journal cleanup
[info] A Journal optionally
[info] + CapabilityFlag `supportsRejectingNonSerializableObjects` was turned `off`. To enable the related tests override it with `CapabilityFlag.on` (or `true` in Scala). 
[info] + CapabilityFlag `supportsSerialization` was turned `on`.  
[info] - may serialize events
[info] Run completed in 52 seconds, 904 milliseconds.
[info] Total number of tests run: 15
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 15, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 63 s, completed Aug 19, 2019 2:02:01 AM

```

### Query Side

The current version supports Persistence Read Side. The following two queries have been added. 

- `currentPersistenceIds()` gives back the persistence ids in a bounded stream that is closed after completion
- `persistenceIds()` gives back the persistence ids in an unbounded stream that keeps open. New persistence ids will pushed into this stream.

```scala
object QueryExample extends App {

  implicit val system = ActorSystem("example")

  implicit val mat = ActorMaterializer()


  val readJournal =
    PersistenceQuery(system).readJournalFor[MapRDBScalaReadJournal]("akka.persistence.query.journal")

  val boundedStream = readJournal.currentPersistenceIds().runForeach(println)

  val unboundedStream = readJournal.persistenceIds().runForeach(println)

  Await.result(boundedStream, scala.concurrent.duration.Duration.Inf)
  Await.result(unboundedStream, scala.concurrent.duration.Duration.Inf)
}
```

