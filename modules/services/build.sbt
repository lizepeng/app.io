Common.settings

name := s"${Common.appName}.services"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  ws,
  "com.sksamuel.elastic4s" %% "elastic4s-core"        % "1.7.0",
  "com.typesafe.play"      %% "play-mailer"           % "3.0.1",
  "com.typesafe.akka"      %% "akka-persistence"      % "2.4.0",
  "com.typesafe.akka"      %% "akka-cluster-tools"    % "2.4.0",
  "com.typesafe.akka"      %% "akka-cluster-sharding" % "2.4.0",
  "com.typesafe.akka"      %% "akka-testkit"          % "2.4.0"   % "test",
  "com.typesafe.akka"      %% "akka-persistence-tck"  % "2.4.0"   % "test",
  "org.cassandraunit"       % "cassandra-unit"        % "2.1.9.2" % "test"
)

libraryDependencies += specs2 % Test

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false