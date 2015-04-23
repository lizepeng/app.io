name := "app-io.services"

Common.settings

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "com.sksamuel.elastic4s" %% "elastic4s"    % "1.5.4",
  "com.typesafe.play"      %% "play-mailer"  % "2.4.0"
)