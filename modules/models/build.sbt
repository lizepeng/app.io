Common.settings

name := s"${Common.appName}.models"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "com.websudos" %% "phantom-dsl"       % "1.11.0",
  "com.websudos" %% "phantom-zookeeper" % "1.11.0"
)

libraryDependencies += specs2 % Test

resolvers ++= Seq(
  "Websudos bintray releases"     at "https://dl.bintray.com/websudos/oss-releases/"
)