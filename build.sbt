name := "app-io"

version := "0.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.10.4"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  filters,
  "com.websudos" %%  "phantom-dsl"       % "1.4.0",
  "com.websudos" %%  "phantom-zookeeper" % "1.4.0",
  "org.webjars"  %   "bootstrap"         % "3.3.1",
  "org.webjars"  %   "font-awesome"      % "4.2.0",
  "org.webjars"  %   "underscorejs"      % "1.4.4",
  "org.webjars"  %   "backbonejs"        % "1.0.0",
  "org.webjars"  %   "holderjs"          % "2.4.0"
)

resolvers ++= Seq(
  "twitter-repo"  at "http://maven.twttr.com",
  "websudos-repo" at "http://maven.websudos.co.uk/ext-release-local"
)

TwirlKeys.templateImports ++= Seq(
  "java.util._",
  "org.joda.time._",
  "play.api.i18n.{Messages => MSG}",
  "models.helpers._",
  "models.cfs._",
  "views.utils._"
)

PlayKeys.routesImport ++= Seq(
  "java.util.UUID",
  "models.cfs.Path",
  "models.helpers.Pager"
)
