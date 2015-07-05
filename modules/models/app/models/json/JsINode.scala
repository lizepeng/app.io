package models.json

import java.util.UUID

import models.cfs._
import org.joda.time.DateTime
import play.api.libs.json.Json

/**
 * @author zepeng.li@gmail.com
 */
trait JsINode {

  def id: UUID

  def name: String

  def path: Path

  def owner_id: UUID

  def created_at: DateTime

  def is_directory: Boolean

  def is_file: Boolean
}

case class JsFile(
  id: UUID,
  name: String,
  path: Path,
  size: Long,
  owner_id: UUID,
  created_at: DateTime,
  is_directory: Boolean,
  is_file: Boolean
) extends JsINode

object JsFile {

  implicit val json_writes = Json.writes[JsFile]

  def from(f: File) =
    JsFile(
      f.id,
      f.name,
      f.path,
      f.size,
      f.owner_id,
      f.created_at,
      f.is_directory,
      !f.is_directory
    )
}

case class JsDirectory(
  id: UUID,
  name: String,
  path: Path,
  owner_id: UUID,
  created_at: DateTime,
  is_directory: Boolean,
  is_file: Boolean
) extends JsINode

object JsDirectory {

  implicit val json_writes = Json.writes[JsDirectory]

  def from(d: Directory) =
    JsDirectory(
      d.id,
      d.name,
      d.path,
      d.owner_id,
      d.created_at,
      d.is_directory,
      !d.is_directory
    )
}