name := "app-io.api"

version := "0.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.2"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  filters,
  "com.typesafe.play" %% "play-mailer"       % "2.4.0",
  "com.websudos"      %% "phantom-dsl"       % "1.5.0",
  "com.websudos"      %% "phantom-zookeeper" % "1.5.0",
  "org.webjars"       %  "bootstrap"         % "3.3.1",
  "org.webjars"       %  "font-awesome"      % "4.3.0",
  "org.webjars"       %  "underscorejs"      % "1.4.4",
  "org.webjars"       %  "backbonejs"        % "1.0.0",
  "org.webjars"       %  "holderjs"          % "2.4.0",
  "org.webjars"       %  "fuelux"            % "3.3.1",
  "org.webjars.bower" % "angular-xeditable"  % "0.1.9",
  "org.webjars.bower" % "angular-bootstrap"  % "0.12.1"
)

resolvers ++= Seq(
  "twitter-repo"  at "http://maven.twttr.com",
  "websudos-repo" at "http://maven.websudos.co.uk/ext-release-local"
)

TwirlKeys.templateImports ++= Seq(
  "java.util.UUID",
  "org.joda.time._",
  "play.api.i18n.{Messages => MSG}"
)

PlayKeys.routesImport ++= Seq(
  "java.util.UUID",
  "org.joda.time.DateTime",
  "play.api.i18n.Lang"
)