Common.settings

name := s"${Common.appName}.models"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "com.websudos" %% "phantom-dsl"       % "1.8.12",
  "com.websudos" %% "phantom-zookeeper" % "1.8.12"
)

resolvers ++= Seq(
  "Typesafe repository snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
  "Typesafe repository releases"  at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype repo"                 at "https://oss.sonatype.org/content/groups/scala-tools/",
  "Sonatype releases"             at "https://oss.sonatype.org/content/repositories/releases",
  "Sonatype snapshots"            at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype staging"              at "http://oss.sonatype.org/content/repositories/staging",
  "Java.net Maven2 Repository"    at "http://download.java.net/maven/2/",
  "Twitter Repository"            at "http://maven.twttr.com"
)