// Comment to get more information during initialization
logLevel := Level.Warn

// Resolvers
resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// Sbt plugins
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.9")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.4")
