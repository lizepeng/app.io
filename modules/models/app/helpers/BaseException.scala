package helpers

/**
 * @author zepeng.li@gmail.com
 */
abstract class BaseException(val code: String)
  extends Exception with Loggable {

  override def getMessage = code
}
