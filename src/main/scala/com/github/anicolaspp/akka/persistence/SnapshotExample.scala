package com.github.anicolaspp.akka.persistence

import akka.actor.{ActorSystem, Props}
import akka.persistence.query.PersistenceQuery
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import akka.stream.ActorMaterializer
import com.github.anicolaspp.akka.persistence.query.MapRDBScalaReadJournal

import scala.concurrent.Await

object SnapshotExample extends App {
  final case class ExampleState(received: List[String] = Nil) {
    def updated(s: String): ExampleState = copy(s :: received)
    override def toString = received.reverse.toString
  }

  class ExamplePersistentActor extends PersistentActor {
    def persistenceId: String = "sample-id-3"

    var state = ExampleState()

    def receiveCommand: Receive = {
      case "print"                               => println("current state = " + state)
      case "snap"                                => saveSnapshot(state)
      case SaveSnapshotSuccess(metadata)         => // ...
      case SaveSnapshotFailure(metadata, reason) => // ...
      case s: String =>
        persist(s) { evt => state = state.updated(evt) }
    }

    def receiveRecover: Receive = {
      case SnapshotOffer(_, s: ExampleState) =>
        println("offered state = " + s)
        state = s
      case evt: String =>
        state = state.updated(evt)
    }

//    override def journalPluginId: String = "akka-persistence-maprdb"
  }

  val system = ActorSystem("example")
  val persistentActor = system.actorOf(Props(classOf[ExamplePersistentActor]), "persistentActor-3-scala")

  Thread.sleep(10000)
  persistentActor ! "a"
  persistentActor ! "b"
  persistentActor ! "snap"
  persistentActor ! "c"
  persistentActor ! "d"
  persistentActor ! "print"

  Thread.sleep(1000*60*60)
  system.terminate()
}

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
