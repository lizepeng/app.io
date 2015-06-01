Common.settings

name := s"${Common.appName}.services"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "com.sksamuel.elastic4s" %% "elastic4s"    % "1.5.4",
  "com.typesafe.play"      %% "play-mailer"  % "3.0.0-RC1",
  "com.typesafe.akka"      %% "akka-contrib" % "2.3.11",
  "com.typesafe.akka"      %% "akka-testkit" % "2.3.11"
)