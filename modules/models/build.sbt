name := "app-io.models"

Common.settings

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "com.websudos"      %% "phantom-dsl"       % "1.5.0",
  "com.websudos"      %% "phantom-zookeeper" % "1.5.0"
)

resolvers ++= Seq(
  "twitter-repo"  at "http://maven.twttr.com",
  "websudos-repo" at "http://maven.websudos.co.uk/ext-release-local"
)