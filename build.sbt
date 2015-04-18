name := "app-io"

Common.settings

scalacOptions += "-feature"

lazy val models   = (project in file("modules/models"))
                    .enablePlugins(PlayScala)

lazy val security = (project in file("modules/security"))
                    .enablePlugins(PlayScala)
                    .dependsOn(models).aggregate(models)

lazy val api      = (project in file("modules/api"))
                    .enablePlugins(PlayScala)
                    .dependsOn(security).aggregate(security)

lazy val root     = (project in file("."))
                    .enablePlugins(PlayScala)
                    .dependsOn(api).aggregate(api)

libraryDependencies ++= Seq(
  filters,
  "com.typesafe.play" %% "play-mailer"       % "2.4.0",
  "org.webjars"       %  "bootstrap"         % "3.3.1",
  "org.webjars"       %  "font-awesome"      % "4.3.0",
  "org.webjars"       %  "underscorejs"      % "1.4.4",
  "org.webjars"       %  "backbonejs"        % "1.0.0",
  "org.webjars"       %  "holderjs"          % "2.4.0",
  "org.webjars"       %  "fuelux"            % "3.3.1",
  "org.webjars.bower" % "angular-xeditable"  % "0.1.9",
  "org.webjars.bower" % "angular-bootstrap"  % "0.12.1",
  "org.webjars.bower" % "angular-resource"   % "1.3.15"
)

TwirlKeys.templateImports ++= Seq(
  "helpers._",
  "java.util.UUID",
  "models.cfs._",
  "org.joda.time._",
  "play.api.i18n.{Messages => MSG}",
  "security._"
)

PlayKeys.routesImport ++= Seq(
  "helpers._",
  "java.util.UUID",
  "org.joda.time.DateTime",
  "models.cfs._",
  "play.api.i18n.Lang",
  "scala.language.reflectiveCalls"
)