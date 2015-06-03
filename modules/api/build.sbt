Common.settings

name := s"${Common.appName}.api"

scalacOptions += "-feature"

routesGenerator := InjectedRoutesGenerator

routesImport ++= Seq(
  "helpers._",
  "java.util.UUID",
  "org.joda.time.DateTime",
  "models.cfs._",
  "play.api.i18n.Lang",
  "scala.language.reflectiveCalls"
)