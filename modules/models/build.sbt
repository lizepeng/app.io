Common.settings

name := s"${Common.appName}.models"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "com.websudos" %% "phantom-dsl"       % "1.8.12",
  "com.websudos" %% "phantom-zookeeper" % "1.8.12"
)

libraryDependencies += specs2 % Test