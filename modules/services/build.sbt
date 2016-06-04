Common.settings

name := s"${Common.appName}.services"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  ws,
  "com.sksamuel.elastic4s" %% "elastic4s-core"        % "2.2.0",
  "com.typesafe.play"      %% "play-mailer"           % "5.0.0-M1",
  "com.typesafe.akka"      %% "akka-persistence"      % "2.4.4",
  "com.typesafe.akka"      %% "akka-cluster-tools"    % "2.4.4",
  "com.typesafe.akka"      %% "akka-cluster-sharding" % "2.4.4",
  "com.typesafe.akka"      %% "akka-slf4j"            % "2.4.4",
  "com.typesafe.akka"      %% "akka-testkit"          % "2.4.4"  % "test",
  "com.typesafe.akka"      %% "akka-persistence-tck"  % "2.4.4"  % "test"
)

// Two cassandra starting process could not be launched simultaneously
concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false