package controllers

import helpers._
import models._
import org.joda.time._
import play.api.Configuration
import play.api.i18n._
import play.api.mvc.BodyParsers.parse
import play.api.mvc._
import protocols.JsonProtocol._
import protocols._
import security.ModulesAccessControl._
import security._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
object RateLimitChecker {

  def apply(
    shouldIncrement: Boolean = true
  )(
    implicit
    resource: CheckedModule,
    _basicPlayApi: BasicPlayApi,
    params: RateLimit.Params,
    _rateLimits: RateLimits,
    eh: UserActionExceptionHandler
  ) = new ActionFunction[UserRequest, UserRequest]
    with BasicPlayComponents
    with DefaultPlayExecutor {
    override def invokeBlock[A](
      req: UserRequest[A],
      block: (UserRequest[A]) => Future[Result]
    ): Future[Result] = {
      RateLimit.Check(req, req.user, shouldIncrement).fold[Result](
        identity,
        limit => block(req).map(limit.setHeaders)
      )
    }
    def basicPlayApi = _basicPlayApi
  }

  def Parser()(
    implicit
    resource: CheckedModule,
    _basicPlayApi: BasicPlayApi,
    params: RateLimit.Params,
    _rateLimits: RateLimits,
    eh: BodyParserExceptionHandler
  ) = new BodyParserFunction[UserRequestHeader, UserRequestHeader]
    with BasicPlayComponents
    with DefaultPlayExecutor {
    override def invoke[B](
      req: UserRequestHeader,
      block: UserRequestHeader => Future[BodyParser[B]]
    ): Future[BodyParser[B]] = {
      RateLimit.Check(req, req.user, shouldIncrement = true).fold[BodyParser[B]](
        r => Future.successful(parse.error[B](r)),
        _ => block(req)
      )
    }
    def basicPlayApi = _basicPlayApi
  }
}

/**
 * The current status of rate limit
 *
 * @param count  the current count
 * @param params parameters of rate limit
 */
case class RateLimit(count: Long, params: RateLimit.Params) extends ExtHeaders {

  val exceeded  = count >= params.conf.limit
  val remaining = if (exceeded) 0 else params.conf.limit - count - 1

  def setHeaders(result: Result) = result.withHeaders(
    X_RATE_LIMIT_LIMIT -> params.conf.limit.toString,
    X_RATE_LIMIT_REMAINING -> remaining.toString,
    X_RATE_LIMIT_RESET -> params.reset.toString
  )
}

object RateLimit {

  /** Unit of rate limit
   *
   * @param limit max value of permitted request in a span
   * @param span  every n minutes from o'clock
   */
  case class Config(limit: Int, span: Int)

  /**
   * Parameters of rate limit
   *
   * @param conf the configuration of rate limit
   * @param now  time stamp
   */
  case class Params(conf: Config, now: DateTime = DateTime.now) {

    val minutes = now.getMinuteOfHour
    val seconds = now.getSecondOfMinute
    val reset   = (conf.span - minutes % conf.span) * 60 - seconds
    val floor   = now.hourOfDay.roundFloorCopy.plusMinutes((minutes / conf.span) * conf.span)
  }

  /**
   * The real process of rate limit checking
   *
   * @param request         request header
   * @param user            user
   * @param shouldIncrement if false then don't increment the counter, since it may be incremented by body parser
   */
  case class Check(
    request: RequestHeader,
    user: User,
    shouldIncrement: Boolean = true
  )(
    implicit
    val resource: CheckedModule,
    val basicPlayApi: BasicPlayApi,
    val _rateLimits: RateLimits,
    val params: Params,
    val eh: ExceptionHandler
  ) extends BasicPlayComponents
    with DefaultPlayExecutor
    with I18nLoggingComponents
    with I18nSupport {

    def fold[A](
      failure: Future[Result] => Future[A],
      success: RateLimit => Future[A]
    ): Future[A] = _rateLimits
      .get(resource.name, params.floor)(user)
      .map(RateLimit(_, params))
      .flatMap { limit =>
        if (limit.exceeded) {
          val result = Results.TooManyRequests {
            JsonMessage(s"${resource.name}.exceeded")(request2Messages(request))
          }
          failure(Future.successful(limit.setHeaders(result)))
        }
        else for {
          ___ <- {
            if (!shouldIncrement) Future.successful(Unit)
            else _rateLimits.inc(resource.name, limit.params.floor)(user)
          }
          ret <- success(limit)
        } yield ret
      }.andThen {
      case Failure(e: BaseException) => Logger.debug(s"RateLimitChecker failed, because ${e.reason}", e)
      case Failure(e: Throwable)     => Logger.error(s"RateLimitChecker failed.", e)
    }.recoverWith {
      case _: Throwable => failure(Future.successful(eh.onThrowable(request)))
    }
  }
}

trait RateLimitConfigComponents {
  self: CanonicalNamed =>

  def configuration: Configuration

  lazy val rateLimitConfig = RateLimit.Config(
    configuration
      .getInt(s"$canonicalName.rate_limit.limit")
      .orElse(configuration.getInt(s"$packageName.rate_limit.limit"))
      .getOrElse(900),
    configuration
      .getMilliseconds(s"$canonicalName.rate_limit.span")
      .orElse(configuration.getMilliseconds(s"$packageName.rate_limit.span"))
      .map(_ millis)
      .map(_.toMinutes.toInt).getOrElse(15)
  )

  implicit def rateLimitParams = RateLimit.Params(rateLimitConfig)
}