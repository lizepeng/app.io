import batches.ReIndexInternalGroups
import com.websudos.phantom.connectors.ContactPoint.DefaultPorts
import elasticsearch._ 
import filters._
import helpers._
import messages._
import models._
import models.cassandra.KeySpaceBuilder
import models.cfs._
import models.sys.SysConfigs
import play.api.ApplicationLoader.Context
import play.api.i18n._
import play.api.inject.{NewInstanceInjector, SimpleInjector}
import play.api.libs.ws.ning.NingWSComponents
import play.api.mvc._
import router.Routes
import services.actors.ResourcesMediator
import services.web.ip_api.IPService
import services.{BandwidthService, MailService}

import scala.concurrent.Future
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
class Components(context: Context)
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
    new KeySpaceBuilder(
      applicationLifecycle, _
        .addContactPoints(
          configuration
            .getStringSeq("cassandra.contact_points")
            .getOrElse(Seq("localhost")): _*
        )
        .withPort(DefaultPorts.live)
    )

  // Basic Play Api
  implicit val basicPlayApi = BasicPlayApi(
    langs, messagesApi, configuration, applicationLifecycle, actorSystem
  )

  // Services
  implicit val bandwidth   = new BandwidthService(basicPlayApi)
  implicit val mailService = new MailService(basicPlayApi)
  implicit val es          = new ElasticSearch(basicPlayApi)
  implicit val ipService   = new IPService(basicPlayApi, wsClient)

  // Register permission checkable controllers
  implicit val secured = new controllers.RegisteredSecured(
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

  // Models
  implicit val _sysConfig      = new SysConfigs
  implicit val _accessControls = new AccessControls
  implicit val _internalGroups = new InternalGroups(
    ig => Future.successful(Unit),
    implicit ig => Future.sequence(
      Seq(
        ReIndexInternalGroups(es, ig).start(),
        controllers.AccessControlsCtrl.initIfFirstRun,
        controllers.Layouts.initIfFirstRun
      )
    ).andThen {
      case Success(_) => play.api.Logger.info("System has been initialized.")
    }
  )

  implicit val _ipRateLimits           = new IPRateLimits
  implicit val _userLoginIPs           = new UserLoginIPs
  implicit val _users                  = new Users
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

  // Actors
  startActors()

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
  val userWebSocketCtrl = new controllers.sockets.UserWebSocketCtrl

  // Sockets Router
  val socketsRouter = new sockets.Routes(
    errorHandler,
    userWebSocketCtrl
  )

  // Private Api Controllers
  val apiPrivatePingCtrl = new controllers.api_private.PingCtrl

  // Private Api Router
  val apiPrivateRouter = new api_private.Routes(
    errorHandler,
    apiPrivatePingCtrl
  )

  // Permission Checking
  implicit val permCheckRequired =
    controllers.UserActionRequired(_groups, _accessControls)

  // Root Controllers
  val applicationCtrl    = new controllers.Application()
  val experimentalCtrl   = new controllers.ExperimentalCtrl()
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
    experimentalCtrl,
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
      case "LoopbackIPFilter"          => new LoopbackIPFilter()
      case "InvalidIPFilter"           => new InvalidIPFilter()
      case "RateLimitExceededIPFilter" => new RateLimitExceededIPFilter()
      case "Compressor"                => new Compressor()
      case "RequestLogger"             => new RequestLogger()
      case "RequestTimeLogger"         => new RequestTimeLogger()
    }

  // temporary workaround until issue #4614 in play framework is fixed. See https://github.com/playframework/playframework/issues/4614
  override lazy val injector =
    new SimpleInjector(NewInstanceInjector) +
      router.asInstanceOf[play.api.routing.Router] +
      crypto +
      httpConfiguration +
      actorSystem

  def startActors(): Unit = {
    actorSystem.actorOf(ResourcesMediator.props, ResourcesMediator.basicName)

    //Start Actor ShardRegion
    MailActor.startRegion(configuration, actorSystem)
    ChatActor.startRegion(configuration, actorSystem)
    NotificationActor.startRegion(configuration, actorSystem)
  }
}