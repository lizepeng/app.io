import batches.ReIndexInternalGroups
import com.websudos.phantom.connectors.ContactPoint.DefaultPorts
import elasticsearch.{ESIndexCleaner, ElasticSearch}
import filters._
import helpers._
import messages.{ChatActor, MailActor}
import models._
import models.actors.ResourcesMediator
import models.cassandra.KeySpaceBuilder
import models.cfs._
import models.sys.SysConfigs
import play.api.ApplicationLoader.Context
import play.api.i18n._
import play.api.inject.{NewInstanceInjector, SimpleInjector}
import play.api.libs.ws.ning.NingWSComponents
import play.api.mvc._
import router.Routes
import services.web.ip_api.IPService
import services.{BandwidthService, MailService}

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
abstract class Components(context: Context)
  extends play.api.BuiltInComponentsFromContext(context)
  with I18nComponents
  with NingWSComponents
  with DefaultPlayExecutor {

  play.api.Logger.configure(context.environment)

  // Prevent thread leaks in dev mode
  applicationLifecycle.addStopHook(() => LeakedThreadsKiller.killPlayInternalExecutionContext())
  applicationLifecycle.addStopHook(() => LeakedThreadsKiller.killTimerInConcurrent())

  // Cassandra Connector
  implicit val contactPoint: KeySpaceBuilder =
    new KeySpaceBuilder(applicationLifecycle, _.addContactPoint("localhost").withPort(DefaultPorts.live))

  // Basic Play Api
  implicit val basicPlayApi = BasicPlayApi(
    langs, messagesApi, configuration, applicationLifecycle, actorSystem
  )

  // Services
  implicit val bandwidth   = new BandwidthService(basicPlayApi)
  implicit val mailService = new MailService(basicPlayApi)
  implicit val es          = new ElasticSearch(basicPlayApi)
  implicit val ipService   = new IPService(basicPlayApi, wsClient)

  // Models
  implicit val _sysConfig      = new SysConfigs
  implicit val _internalGroups = new InternalGroups(
    ESIndexCleaner(_).dropIndexIfEmpty,
    ReIndexInternalGroups(es, _).start()
  )

  implicit val _ipRateLimits           = new IPRateLimits
  implicit val _users                  = new Users
  implicit val _accessControls         = new AccessControls
  implicit val _sessionData            = new SessionData
  implicit val _expirableLinks         = new ExpirableLinks
  implicit val _rateLimits             = new RateLimits
  implicit val _cfs                    = new CassandraFileSystem
  implicit val _groups                 = new Groups
  implicit val _persons                = new Persons
  implicit val _emailTemplates         = new EmailTemplates
  implicit val _emailTemplateHistories = new EmailTemplateHistories
  implicit val _chatHistories          = new ChatHistories
  implicit val _mailInbox              = new MailInbox
  implicit val _mailSent               = new MailSent

  // Error Handler
  val errorHandler = new ErrorHandler(environment, configuration, sourceMapper, Some(router))

  // Internal Api Permission Checking
  implicit val apiInternalPermCheckRequired =
    controllers.api_internal.UserActionRequired(_users, _accessControls, _rateLimits)

  // Internal Api Controllers
  val apiInternalSearchCtrl         = new controllers.api_internal.SearchCtrl
  val apiInternalIPCtrl             = new controllers.api_internal.IPCtrl
  val apiInternalGroupsCtrl         = new controllers.api_internal.GroupsCtrl
  val apiInternalUsersCtrl          = new controllers.api_internal.UsersCtrl
  val apiInternalAccessControlsCtrl = new controllers.api_internal.AccessControlsCtrl
  val apiInternalFileSystemCtrl     = new controllers.api_internal.FileSystemCtrl

  // Internal Api Router
  val apiInternalRouter = new api_internal.Routes(
    errorHandler,
    apiInternalSearchCtrl,
    apiInternalIPCtrl,
    apiInternalGroupsCtrl,
    apiInternalUsersCtrl,
    apiInternalAccessControlsCtrl,
    apiInternalFileSystemCtrl
  )

  // Sockets Controllers
  val socketsChatCtrl = new controllers.sockets.ChatCtrl

  // Sockets Router
  val socketsRouter = new sockets.Routes(
    errorHandler,
    socketsChatCtrl
  )

  // Private Api Controllers
  val apiPrivatePingCtrl = new controllers.api_private.PingCtrl

  // Private Api Router
  val apiPrivateRouter = new api_private.Routes(
    errorHandler,
    apiPrivatePingCtrl
  )

  // Register permission checkable controllers
  implicit val secured = new controllers.RegisteredSecured(
    messagesApi,
    Seq(
      controllers.AccessControlsCtrl,
      controllers.api_internal.AccessControlsCtrl,
      controllers.UsersCtrl,
      controllers.api_internal.UsersCtrl,
      controllers.GroupsCtrl,
      controllers.api_internal.GroupsCtrl,
      controllers.FileSystemCtrl,
      controllers.api_internal.FileSystemCtrl,
      controllers.EmailTemplatesCtrl,
      controllers.api_internal.SearchCtrl
    )
  )

  // Permission Checking
  implicit val permCheckRequired =
    controllers.UserActionRequired(_groups, _accessControls)

  // Root Controllers
  val applicationCtrl    = new controllers.Application()
  val chatCtrl           = new controllers.ChatCtrl()
  val fileSystemCtrl     = new controllers.FileSystemCtrl()
  val sessionsCtrl       = new controllers.SessionsCtrl()
  val usersCtrl          = new controllers.UsersCtrl()
  val myCtrl             = new controllers.MyCtrl()
  val groupsCtrl         = new controllers.GroupsCtrl()
  val passwordResetCtrl  = new controllers.PasswordResetCtrl()
  val emailTemplatesCtrl = new controllers.EmailTemplatesCtrl()
  val accessControlsCtrl = new controllers.AccessControlsCtrl()

  // Root Router
  lazy val router: Routes = new Routes(
    errorHandler,
    apiInternalRouter,
    socketsRouter,
    apiPrivateRouter,
    applicationCtrl,
    chatCtrl,
    new controllers.Assets(errorHandler),
    fileSystemCtrl,
    sessionsCtrl,
    usersCtrl,
    myCtrl,
    groupsCtrl,
    passwordResetCtrl,
    emailTemplatesCtrl,
    accessControlsCtrl
  )

  // Http Filters
  override lazy val httpFilters: Seq[EssentialFilter] =
    configuration.getStringSeq("app.http.filters").getOrElse(Nil).collect {
      case "LoopbackIPFilter"  => new LoopbackIPFilter()
      case "ClientIPFilter"    => new ClientIPFilter()
      case "Compressor"        => new Compressor()
      case "RequestLogger"     => new RequestLogger()
      case "RequestTimeLogger" => new RequestTimeLogger()
    }

  start()

  def start(): Unit

  def startActors(): Unit = {
    actorSystem.actorOf(ResourcesMediator.props, ResourcesMediator.basicName)

    //Start Actor ShardRegion
    MailActor.startRegion(actorSystem)
    ChatActor.startRegion(actorSystem)
  }

  def startSystem(): Unit = {
    Future.sequence(
      Seq(
        controllers.Layouts.init,
        controllers.AccessControlsCtrl.initIfEmpty
      )
    ).onSuccess {
      case _ => play.api.Logger.info("System has started")
    }
  }

  // temporary workaround until issue #4614 in play framework is fixed. See https://github.com/playframework/playframework/issues/4614
  override lazy val injector =
    new SimpleInjector(NewInstanceInjector) +
      router.asInstanceOf[play.api.routing.Router] +
      crypto +
      httpConfiguration +
      actorSystem
}