Common.settings

name := s"${Common.appName}"

scalacOptions += "-feature"

LessKeys.compress in Assets := true

pipelineStages := Seq(uglify, gzip)

lazy val models   = (project in file("modules/models"))
                    .enablePlugins(PlayScala)

lazy val security = (project in file("modules/security"))
                    .enablePlugins(PlayScala)
                    .dependsOn(models).aggregate(models)

lazy val services = (project in file("modules/services"))
                    .enablePlugins(PlayScala)
                    .dependsOn(models).aggregate(models)

lazy val api      = (project in file("modules/api"))
                    .enablePlugins(PlayScala)
                    .dependsOn(security, services).aggregate(security, services)

lazy val sockets  = (project in file("modules/sockets"))
                    .enablePlugins(PlayScala)
                    .dependsOn(security, services).aggregate(security, services)

lazy val root     = (project in file("."))
                    .enablePlugins(PlayScala)
                    .dependsOn(api, sockets).aggregate(api, sockets)

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  filters,
  "org.webjars"       %  "bootstrap"         % "3.3.1",
  "org.webjars"       %  "font-awesome"      % "4.3.0",
  "org.webjars"       %  "holderjs"          % "2.4.0",
  "org.webjars"       %  "fuelux"            % "3.3.1",
  "org.webjars.bower" % "angular-xeditable"  % "0.1.9",
  "org.webjars.bower" % "angular-bootstrap"  % "0.12.1",
  "org.webjars.bower" % "angular-resource"   % "1.3.15",
  "org.webjars.bower" % "underscore"         % "1.8.3",
  "org.webjars"       % "ng-flow"            % "2.6.1"
)

TwirlKeys.templateImports ++= Seq(
  "helpers._",
  "java.util.UUID",
  "models.cfs._",
  "models.json._",
  "org.joda.time._",
  "play.api.i18n.{Messages => MSG}",
  "security._"
)

routesImport ++= Seq(
  "helpers._",
  "java.util.UUID",
  "org.joda.time.DateTime",
  "models.cfs._",
  "play.api.i18n.Lang",
  "scala.language.reflectiveCalls"
)