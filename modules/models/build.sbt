Common.settings

name := s"${Common.appName}.models"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "org.mindrot"       %  "jbcrypt"           % "0.3m",
  "com.websudos"      %% "phantom-dsl"       % "1.22.0",
  "com.websudos"      %% "phantom-zookeeper" % "1.22.0",
  "org.cassandraunit" %  "cassandra-unit"    % "2.2.2.1" % Test
)

libraryDependencies += specs2 % Test

resolvers ++= Seq(
  "Websudos bintray releases" at "https://dl.bintray.com/websudos/oss-releases/",
  "scalaz-bintray"            at "https://dl.bintray.com/scalaz/releases"
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false