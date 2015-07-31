package protocols

/**
 * @author zepeng.li@gmail.com
 */
trait ExHeaders {

  val LINK                   = "Link"
  val X_RATE_LIMIT_LIMIT     = "X-Rate-Limit-Limit"
  val X_RATE_LIMIT_REMAINING = "X-Rate-Limit-Remaining"
  val X_RATE_LIMIT_RESET     = "X-Rate-Limit-Reset"
}