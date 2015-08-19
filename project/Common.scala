import sbt._
import Keys._

object Common {
  val settings: Seq[Setting[_]] = Seq(
    organization := "io.app",
    version      := "0.1-SNAPSHOT",
    scalaVersion := "2.11.7"
  )

  val appName = "app-io"
}