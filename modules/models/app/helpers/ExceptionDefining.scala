package helpers

/**
 * @author zepeng.li@gmail.com
 */
trait ExceptionDefining {
  self: CanonicalNamed =>

  def error_code(key: String) = s"$canonicalName.$key"
}
