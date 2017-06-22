import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import sbt.Keys._
import sbtrelease.{Version, versionFormatError}
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

organization := "com.interelgroup"
name := "core3"
licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Some(url("https://github.com/Interel-Group/core3"))

scalaVersion in ThisBuild := "2.11.11"

lazy val core3 = (project in file("."))
  .settings(SbtMultiJvm.multiJvmSettings)
  .settings(
    crossScalaVersions := Seq("2.11.11"), //TODO - 2.12 support requires Play 2.6
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
      dependencies_test             map (_ % Test)
    ).flatten,
    dependencyOverrides ++= overrides_netty,
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

//Meta Settings
lazy val macroSettings = Seq(
  addCompilerPlugin("org.scalameta" % "paradise" % "3.0.0-M9" cross CrossVersion.full),
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
lazy val akkaVersion = "2.4.17"
lazy val nettyOverrideVersion = "4.0.41.Final"

lazy val dependencies_base = Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.7",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.7",
  "com.typesafe.play" %% "play-logback" % "2.5.13",
  cache,
  ws,
  filters,
  "com.github.nscala-time" %% "nscala-time" % "2.16.0",
  "com.pauldijou" %% "jwt-play-json" % "0.12.1",
  "com.roundeights" %% "hasher" % "1.2.0",
  "com.google.code.findbugs" % "jsr305" % "3.0.1" % Compile
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
  "com.typesafe.slick" %% "slick" % "3.2.0",
  "io.underscore"      %% "slickless" % "0.3.2"
)

lazy val dependencies_cli = Seq(
  "org.jline" % "jline" % "3.2.0",
  "com.github.scopt" %% "scopt" % "3.5.0"
)

lazy val dependencies_mariaDB = Seq(
  "org.mariadb.jdbc" % "mariadb-java-client" % "1.5.9"
)

lazy val dependencies_redis = Seq(
  "com.github.etaty" %% "rediscala" % "1.8.0"
)

lazy val dependencies_elastic = Seq(
  "com.sksamuel.elastic4s" %% "elastic4s-core" % "5.3.2",
  "com.sksamuel.elastic4s" %% "elastic4s-tcp" % "5.3.2"
)

lazy val dependencies_mail = Seq(
  "ch.lightshed" %% "courier" % "0.1.4"
)

lazy val dependencies_distributedCache = Seq(
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.github.blemale" %% "scaffeine" % "2.0.0"
)

lazy val dependencies_test = Seq(
  "org.scalacheck" %% "scalacheck" % "1.13.5",
  "org.scalatest" %% "scalatest" % "3.0.0",
  "com.storm-enroute" %% "scalameter" % "0.7",
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
  "org.jvnet.mock-javamail" % "mock-javamail" % "1.9",
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0"
)

lazy val overrides_netty = Set(
  "io.netty" % "netty-codec-http" % nettyOverrideVersion,
  "io.netty" % "netty-handler" % nettyOverrideVersion,
  "io.netty" % "netty-codec" % nettyOverrideVersion,
  "io.netty" % "netty-transport" % nettyOverrideVersion,
  "io.netty" % "netty-buffer" % nettyOverrideVersion,
  "io.netty" % "netty-common" % nettyOverrideVersion,
  "io.netty" % "netty-transport-native-epoll" % nettyOverrideVersion
)

//Release Config
releaseVersion := {
  v =>
    Version(v).map {
      version =>
        val next = System.getProperty("release-version-bump", "bugfix") match {
          case "major" => version.withoutQualifier.bump(sbtrelease.Version.Bump.Major)
          case "minor" => version.withoutQualifier.bump(sbtrelease.Version.Bump.Minor)
          case "bugfix" => version.withoutQualifier
        }

        next.string
    }.getOrElse(versionFormatError)
}

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  pushChanges
)

//Publish Config
pomIncludeRepository := { _ => false }
publishMavenStyle := true
publishArtifact in Test := false
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

scmInfo := Some(
  ScmInfo(
    url("https://github.com/Interel-Group/core3"),
    "scm:git@github.com:Interel-Group/core3"
  )
)

developers := List(
  Developer(
    id    = "jamesaddinall",
    name  = "James Addinall",
    email = "james.addinall@interelgroup.com",
    url   = url("https://github.com/jamesaddinall")
  ),
  Developer(
    id    = "sndnv",
    name  = "Angel Sanadinov",
    email = "angel.sanadinov@interelgroup.com",
    url   = url("https://github.com/sndnv")
  )
)
