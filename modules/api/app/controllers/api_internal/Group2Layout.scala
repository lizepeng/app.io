package controllers.api_internal

import java.util.UUID

import com.websudos.phantom.dsl.ResultSet
import helpers._
import models.sys._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object Group2Layout extends Group2Layout {

  case class Layout(layout: String) extends AnyVal

  object Layout {implicit val jsonFormat = Json.format[Layout]}

  val layoutSerializer = new SysConfig.Serializer[Layout] {
    def << = Layout.apply

    def >>: = _.layout
  }

  def save(
    gid: UUID, layout: Layout
  )(implicit sysConfig: SysConfigs, ec: ExecutionContext): Future[Layout] = {
    sysConfig.save(
      SysConfigEntry(
        canonicalName, gid.toString, layout.layout
      )
    ).map(_ => layout)
  }

  def getOrElseUpdate(
    gid: UUID, layout: Layout
  )(implicit sysConfig: SysConfigs): Future[Layout] = {
    sysConfig.getOrElseUpdate(
      canonicalName, gid.toString, layout
    )(layoutSerializer)
  }
}

trait Group2Layout extends CanonicalNamed {

  override def basicName = "group_to_layout"

  override def canonicalName = "views.group_to_layout"
}