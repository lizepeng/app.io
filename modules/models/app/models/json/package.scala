package models

import models.cfs.{File, Path}
import play.api.libs.json.Json

/**
 * @author zepeng.li@gmail.com
 */
package object json {

  implicit class ToJsFile(val f: File) extends AnyVal {

    def toJson = Json.toJson(JsFile.from(f))
  }

  implicit class ToJsPath(val p: Path) extends AnyVal {

    def toJson = Json.toJson(p)
  }

}