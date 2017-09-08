pomIncludeRepository in ThisBuild := { _ => false }
publishMavenStyle in ThisBuild := true
publishArtifact in Test := false
publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/Interel-Group/core3"),
    "scm:git@github.com:Interel-Group/core3"
  )
)

developers in ThisBuild := List(
  Developer(
    id    = "jamesaddinall",
    name  = "James Addinall",
    email = "jamesaddinall@users.noreply.github.com",
    url   = url("https://github.com/jamesaddinall")
  ),
  Developer(
    id    = "sndnv",
    name  = "Angel Sanadinov",
    email = "sndnv@users.noreply.github.com",
    url   = url("https://github.com/sndnv")
  )
)
