Common.settings

name := s"${Common.appName}.services"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  ws,
  "com.sksamuel.elastic4s" %% "elastic4s-core"                    % "2.2.0",
  "com.typesafe.play"      %% "play-mailer"                       % "3.0.1",
  "com.typesafe.akka"      %% "akka-contrib"                      % "2.3.13",
  "com.typesafe.akka"      %% "akka-testkit"                      % "2.3.13"  % Test,
  "com.typesafe.akka"      %% "akka-persistence-tck-experimental" % "2.3.13"  % Test
)

// Two cassandra starting process could not be launched simultaneously
concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false