import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import sbt.Keys._

organization in ThisBuild := "com.interelgroup"
name in ThisBuild := "core3"
licenses in ThisBuild := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage in ThisBuild := Some(url("https://github.com/Interel-Group/core3"))

scalaVersion in ThisBuild := "2.12.3"

lazy val core3 = (project in file("."))
  .settings(SbtMultiJvm.multiJvmSettings)
  .settings(
    crossScalaVersions := Seq("2.11.11", "2.12.3"),
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases",
      "lightshed-maven" at "http://dl.bintray.com/content/lightshed/maven"
    ),
    libraryDependencies ++= Seq(
      dependencies_base,
      dependencies_slick,
      dependencies_cli              map (_ % Optional),
      dependencies_mariaDB          map (_ % Optional),
      dependencies_redis            map (_ % Optional),
      dependencies_elastic          map (_ % Optional),
      dependencies_mail             map (_ % Optional),
      dependencies_distributedCache map (_ % Optional),
      dependencies_mariaDB          map (_ % MultiJvm),
      dependencies_distributedCache map (_ % MultiJvm),
      dependencies_meta             map (_ % Test),
      dependencies_test             map (_ % Test)
    ).flatten,
    macroSettings,
    compile in MultiJvm := ((compile in MultiJvm) triggeredBy (compile in Test)).value,
    executeTests in Test := {
      val testResults = (executeTests in Test).value
      val multiNodeResults = (executeTests in MultiJvm).value

      Tests.Output(
        if (testResults.overall.id < multiNodeResults.overall.id) {
          multiNodeResults.overall
        } else {
          testResults.overall
        },
        testResults.events ++ multiNodeResults.events,
        testResults.summaries ++ multiNodeResults.summaries
      )
    },
    testFrameworks in Test += new TestFramework("org.scalameter.ScalaMeterFramework"),
    logBuffered in Test := false,
    parallelExecution in Test := false
  )
  .configs(MultiJvm)
  .dependsOn(meta)
  .aggregate(meta)

//Meta Settings
lazy val macroSettings = Seq(
  addCompilerPlugin("org.scalameta" % "paradise" % "3.0.0-M10" cross CrossVersion.full),
  scalacOptions += "-Xplugin-require:macroparadise",
  scalacOptions in (Compile, console) := Seq()
)

lazy val meta = (project in file("meta"))
  .settings(
    libraryDependencies ++= Seq(
      dependencies_meta       map (_ % Provided),
      dependencies_test       map (_ % Test),
      dependencies_meta_test  map (_ % Test)
    ).flatten,
    macroSettings,
    logBuffered in Test := false,
    parallelExecution in Test := false
  )

//Dependency Definitions
lazy val akkaVersion = "2.5.4"

lazy val dependencies_base = Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.1",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.0",
  "com.typesafe.play" %% "play-logback" % "2.6.3",
  "com.typesafe.play" %% "play-json" % "2.6.3",
  ehcache,
  ws,
  filters,
  "com.pauldijou" %% "jwt-play-json" % "0.14.0",
  "com.roundeights" %% "hasher" % "1.2.0"
)

lazy val dependencies_meta = Seq(
  "org.scalameta" %% "scalameta" % "1.8.0"
)

lazy val dependencies_meta_test = Seq(
  "org.scalameta" %% "testkit" % "1.8.0"
)

lazy val dependencies_slick = Seq(
  jdbc,
  "com.chuusai" %% "shapeless" % "2.3.2",
  "com.typesafe.slick" %% "slick" % "3.2.1",
  "io.underscore" %% "slickless" % "0.3.2"
)

lazy val dependencies_cli = Seq(
  "org.jline" % "jline" % "3.2.0",
  "com.github.scopt" %% "scopt" % "3.5.0"
)

lazy val dependencies_mariaDB = Seq(
  "org.mariadb.jdbc" % "mariadb-java-client" % "2.1.1"
)

lazy val dependencies_redis = Seq(
  "com.github.etaty" %% "rediscala" % "1.8.0"
)

lazy val dependencies_elastic = Seq(
  "com.sksamuel.elastic4s" %% "elastic4s-core" % "5.5.3",
  "com.sksamuel.elastic4s" %% "elastic4s-tcp" % "5.5.3"
)

lazy val dependencies_mail = Seq(
  "ch.lightshed" %% "courier" % "0.1.4"
)

lazy val dependencies_distributedCache = Seq(
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.github.blemale" %% "scaffeine" % "2.2.0"
)

lazy val dependencies_test = Seq(
  "org.scalacheck" %% "scalacheck" % "1.13.5",
  "org.scalatest" %% "scalatest" % "3.0.4",
  "com.storm-enroute" %% "scalameter" % "0.8.2",
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
  "org.jvnet.mock-javamail" % "mock-javamail" % "1.9",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.1"
)
