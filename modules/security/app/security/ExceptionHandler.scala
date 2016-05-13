package security

import play.api.mvc._

/**
 * @author zepeng.li@gmail.com
 */
trait ExceptionHandler {

  def onUnauthorized: RequestHeader => Result
  def onPermissionDenied: RequestHeader => Result
  def onFilePermissionDenied: RequestHeader => Result
  def onPathNotFound: RequestHeader => Result
  def onThrowable: RequestHeader => Result
}

trait BodyParserExceptionHandler extends ExceptionHandler

trait UserActionExceptionHandler extends ExceptionHandler

trait DefaultExceptionHandler extends ExceptionHandler {

  def onUnauthorized: RequestHeader => Result = _ => Results.Unauthorized
  def onPermissionDenied: RequestHeader => Result = _ => Results.NotFound
  def onPathNotFound: RequestHeader => Result = _ => Results.NotFound
  def onFilePermissionDenied: RequestHeader => Result = _ => Results.NotFound
  def onThrowable: RequestHeader => Result = _ => Results.InternalServerError
}