import sbtrelease.{Version, versionFormatError}
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

releaseCrossBuild := true

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
