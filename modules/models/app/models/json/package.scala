package models

import models.cfs._
import play.api.libs.json.Json

/**
 * @author zepeng.li@gmail.com
 */
package object json {

  implicit class ToJsFile(val f: File) extends AnyVal {

    def toJson = Json.toJson(JsFile.from(f))
  }

  implicit class ToJsDirectory(val d: Directory) extends AnyVal {

    def toJson = Json.toJson(JsDirectory.from(d))
  }

  implicit class ToJsPath(val p: Path) extends AnyVal {

    def toJson = Json.toJson(p)
  }

  implicit class ToJsUser(val u: User) extends AnyVal {

    def toJson = Json.toJson(JsUser.from(u))
  }

}