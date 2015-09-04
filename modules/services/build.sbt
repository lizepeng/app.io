Common.settings

name := s"${Common.appName}.services"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  ws,
  "com.sksamuel.elastic4s" %% "elastic4s-core" % "1.7.0",
  "com.typesafe.play"      %% "play-mailer"    % "3.0.0-RC1",
  "com.typesafe.akka"      %% "akka-contrib"   % "2.3.11",
  "com.typesafe.akka"      %% "akka-testkit"   % "2.3.11"
)