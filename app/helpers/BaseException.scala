package helpers

import play.api.i18n.{Lang, Messages}

/**
 * @author zepeng.li@gmail.com
 */
abstract class BaseException(val code: String)
  extends Exception with Product {

  def message(implicit lang: Lang): String = message("msg")

  def reason(implicit lang: Lang): String = message("log")

  def message(key: String)(implicit lang: Lang): String = {
    Messages(
      s"$key.$code",
      productIterator.map(_.toString).toList: _*
    )
  }
}