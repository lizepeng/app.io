import controllers.{AccessControlsCtrl, Application, CFSCtrl, EmailTemplatesCtrl, GroupsCtrl, UsersCtrl, _}
import helpers.BasicPlayApi
import messages.ChatActor
import models._
import models.cfs._
import models.sys.SysConfigs
import play.api.ApplicationLoader.Context
import play.api._
import play.api.i18n._
import router.Routes

import services.{BandwidthService, MailService}

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class AppLoader extends play.api.ApplicationLoader {

  def load(context: Context) = {
    new Components(context).application
  }
}

class Components(context: Context)
  extends play.api.BuiltInComponentsFromContext(context)
  with I18nComponents {

  Logger.configure(context.environment)

  implicit val basicPlayApi   = BasicPlayApi(langs, messagesApi, configuration)
  implicit val sysConfigRepo  = new SysConfigs
  implicit val internalGroups = new InternalGroups

  implicit val emailTemplateRepo        = new EmailTemplates
  implicit val emailTemplateHistoryRepo = new EmailTemplateHistories
  implicit val personRepo               = new Persons
  implicit val userRepo                 = new Users()
  implicit val groupRepo                = new Groups
  implicit val sessionDataDAO           = new SessionData
  implicit val rateLimitRepo            = new RateLimits
  implicit val expirableLinkRepo        = new ExpirableLinks
  implicit val accessControlRepo        = new AccessControls
  implicit val CFS                      = new CFS()
  implicit val permCheck                = PermCheckRequired(userRepo, accessControlRepo)
  implicit val apiPermCheck             = api.PermCheckRequired(userRepo, accessControlRepo, rateLimitRepo)
  implicit val secured                  = buildSecured
  implicit val bandwidth                = BandwidthService(basicPlayApi, actorSystem)
  implicit val elasticSearch            = elasticsearch.ElasticSearch(basicPlayApi)

  val mailService   = MailService(basicPlayApi, actorSystem)
  val apiRouter     = buildApiRouter
  val socketsRouter = buildSocketsRouter

  lazy val router = buildRouter

  Play.start(application)
  lazy     val myComponent = new MyComponent(messagesApi)
  implicit val ec          = actorSystem.dispatcher
  ChatActor.startRegion(actorSystem)
  Future.sequence(
    Seq(
      Schemas.create,
      //TODO
      new api.UsersCtrl().dropIndexIfEmpty,
      new api.GroupsCtrl().dropIndexIfEmpty,
      new api.AccessControlsCtrl().dropIndexIfEmpty,
      groupRepo._internalGroups.initialize.flatMap { done =>
        if (done) new api.GroupsCtrl().reindex
        else Future.successful(false)
      },
      GroupsCtrl.initialize,
      AccessControlsCtrl.initialize
    )
  ).onSuccess {
    case _ => Logger.info("System has started")
  }

  private def buildSecured: RegisteredSecured = {
    new RegisteredSecured(
      messagesApi,
      Seq(
        CFSCtrl,
        GroupsCtrl,
        UsersCtrl,
        EmailTemplatesCtrl,
        AccessControlsCtrl,
        controllers.api.GroupsCtrl,
        controllers.api.UsersCtrl,
        controllers.api.SearchCtrl,
        controllers.api.CFSCtrl,
        controllers.api.AccessControlsCtrl
      )
    )
  }

  private def buildApiRouter = new _root_.api.Routes(
    httpErrorHandler,
    new api.SearchCtrl(),
    new api.GroupsCtrl(),
    new api.UsersCtrl(),
    new api.AccessControlsCtrl(),
    new api.CFSCtrl()
  )

  private def buildSocketsRouter = new _root_.sockets.Routes(
    httpErrorHandler,
    new sockets.Chat()(messagesApi)
  )

  private def buildRouter: Routes = new Routes(
    httpErrorHandler,
    apiRouter,
    socketsRouter,
    new Application(),
    new ChatCtrl(),
    new Assets(httpErrorHandler),
    new CFSCtrl(),
    new SessionsCtrl(),
    new UsersCtrl(elasticSearch),
    new MyCtrl(elasticSearch),
    new GroupsCtrl(),
    new PasswordResetCtrl()(basicPlayApi, mailService, userRepo, expirableLinkRepo, emailTemplateRepo, sysConfigRepo),
    new EmailTemplatesCtrl(),
    new AccessControlsCtrl()
  )

}

class MyComponent(messages: MessagesApi) {

}