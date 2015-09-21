Common.settings

name := s"${Common.appName}.models"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "com.websudos" %% "phantom-dsl"       % "1.12.2",
  "com.websudos" %% "phantom-zookeeper" % "1.12.2"
)

libraryDependencies += specs2 % Test

resolvers ++= Seq(
  "Websudos bintray releases" at "https://dl.bintray.com/websudos/oss-releases/",
  "scalaz-bintray"            at "https://dl.bintray.com/scalaz/releases"
)

publishArtifact in (Compile, packageDoc) := false