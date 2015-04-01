package views.html

import _root_.helpers.syntax.PolarQuestion
import play.api.http.ContentTypes
import play.api.libs.MimeTypes

/**
 * @author zepeng.li@gmail.com
 */
package object common {

  def enclosingName(obj: Any) = obj.getClass.getPackage.getName.split('.').last

}