name := "app-io.api"

Common.settings

scalacOptions += "-feature"

PlayKeys.routesImport ++= Seq(
  "helpers._",
  "java.util.UUID",
  "org.joda.time.DateTime",
  "models.cfs._",
  "play.api.i18n.Lang",
  "scala.language.reflectiveCalls"
)

libraryDependencies ++= Seq(
  "com.sksamuel.elastic4s" %% "elastic4s" % "1.5.4"
)