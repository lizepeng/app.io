import controllers.{AccessControlsCtrl, Application, EmailTemplatesCtrl, CFSCtrl, GroupsCtrl, UsersCtrl, _}
import helpers.BasicPlayApi
import messages.ChatActor
import models._
import models.cfs._
import models.sys.SysConfigRepo
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

  implicit val basicPlayApi = BasicPlayApi(langs, messagesApi, configuration)

  implicit val inodeRepo                = new INodeRepo
  implicit val blockRepo                = new BlockRepo
  implicit val indirectBlockRepo        = new IndirectBlockRepo
  implicit val fileRepo                 = new FileRepo
  implicit val directoryRepo            = new DirectoryRepo
  implicit val sysConfigRepo            = new SysConfigRepo
  implicit val CFS                      = new CFS()
  implicit val Home                     = new Home(CFS)
  implicit val internalGroupsRepo       = new InternalGroupsRepo()
  implicit val emailTemplateRepo        = new EmailTemplateRepo
  implicit val emailTemplateHistoryRepo = new EmailTemplateHistoryRepo
  implicit val personRepo               = new PersonRepo
  implicit val sessionDataDAO           = new SessionDataDAO
  implicit val rateLimitRepo            = new RateLimitRepo
  implicit val expirableLinkRepo        = new ExpirableLinkRepo
  implicit val userRepo                 = new UserRepo()
  implicit val groupRepo                = new GroupRepo
  implicit val accessControlRepo        = new AccessControlRepo

  implicit val secured = buildSecured
  val bandwidth     = BandwidthService(basicPlayApi, actorSystem)
  val mailService   = MailService(basicPlayApi, actorSystem)
  val elasticSearch = elasticsearch.ElasticSearch(basicPlayApi)
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
      new api.UsersCtrl(basicPlayApi, elasticSearch).dropIndexIfEmpty,
      new api.GroupsCtrl(basicPlayApi, elasticSearch).dropIndexIfEmpty,
      new api.AccessControlsCtrl(basicPlayApi, elasticSearch).dropIndexIfEmpty,
      internalGroupsRepo.initialize.flatMap { done =>
        if (done) new api.GroupsCtrl(basicPlayApi, elasticSearch).reindex
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
    new api.SearchCtrl(basicPlayApi, elasticSearch),
    new api.GroupsCtrl(basicPlayApi, elasticSearch),
    new api.UsersCtrl(basicPlayApi, elasticSearch),
    new api.AccessControlsCtrl(basicPlayApi, elasticSearch),
    new api.CFSCtrl(basicPlayApi, bandwidth)
  )

  private def buildSocketsRouter = new _root_.sockets.Routes(
    httpErrorHandler,
    new sockets.Chat()(messagesApi)
  )

  private def buildRouter: Routes = new Routes(
    httpErrorHandler,
    apiRouter,
    socketsRouter,
    new Application(basicPlayApi),
    new ChatCtrl(basicPlayApi),
    new Assets(httpErrorHandler),
    new CFSCtrl(basicPlayApi),
    new SessionsCtrl(basicPlayApi),
    new UsersCtrl(basicPlayApi, elasticSearch),
    new MyCtrl(basicPlayApi, elasticSearch),
    new GroupsCtrl(basicPlayApi),
    new PasswordResetCtrl(basicPlayApi)(mailService, userRepo, expirableLinkRepo, emailTemplateRepo, sysConfigRepo, internalGroupsRepo),
    new EmailTemplatesCtrl(basicPlayApi),
    new AccessControlsCtrl(basicPlayApi)
  )

}

class MyComponent(messages: MessagesApi) {

}