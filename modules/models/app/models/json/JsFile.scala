package models.json

import java.util.UUID

import models.cfs.{File, Path}
import org.joda.time.DateTime
import play.api.libs.json.Json

/**
 * @author zepeng.li@gmail.com
 */
case class JsFile(
  id: UUID,
  name: String,
  path: Path,
  size: Long,
  owner_id: UUID,
  created_at: DateTime,
  is_directory: Boolean
)

object JsFile {

  implicit val js_file_writes = Json.writes[JsFile]

  def from(f: File) =
    JsFile(f.id, f.name, f.path, f.size, f.owner_id, f.created_at, f.is_directory)
}