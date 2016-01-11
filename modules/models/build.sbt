Common.settings

name := s"${Common.appName}.models"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "com.websudos"      %% "phantom-dsl"       % "1.12.2",
  "com.websudos"      %% "phantom-zookeeper" % "1.12.2",
  "org.cassandraunit" %  "cassandra-unit"    % "2.1.9.2" % Test
)

libraryDependencies += specs2 % Test

resolvers ++= Seq(
  "Websudos bintray releases" at "https://dl.bintray.com/websudos/oss-releases/",
  "scalaz-bintray"            at "https://dl.bintray.com/scalaz/releases"
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false