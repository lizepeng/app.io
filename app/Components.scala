import batches.ReIndexInternalGroups
import com.websudos.phantom.connectors.ContactPoint.DefaultPorts
import com.websudos.phantom.connectors._
import elasticsearch._
import filters._
import helpers._
import messages._
import models._
import models.cfs._
import models.sys.SysConfigs
import play.api.ApplicationLoader.Context
import play.api.LoggerConfigurator
import play.api.i18n._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc._
import play.filters.gzip.GzipFilterConfig
import router.Routes
import services._
import services.actors.ResourcesManager
import services.web.ip_api.IPService

import scala.concurrent._
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
class Components(context: Context)
  extends play.api.BuiltInComponentsFromContext(context)
    with I18nComponents
    with AhcWSComponents
    with AppDomainComponents
    with DefaultPlayExecutor
    with Logging {

  LoggerConfigurator(environment.classLoader).foreach {
    _.configure(environment)
  }

  // Prevent thread leaks in dev mode
  applicationLifecycle.addStopHook(() => LeakedThreadsKiller.killPlayInternalExecutionContext())
  applicationLifecycle.addStopHook(() => LeakedThreadsKiller.killTimerInConcurrent())

  // Cassandra Connector
  val contactPoint: KeySpaceBuilder =
    new KeySpaceBuilder(
      _.addContactPoints(
        configuration
          .getStringSeq("cassandra.contact_points")
          .getOrElse(Seq("localhost")): _*
      ).withPort(DefaultPorts.live)
    )

  implicit val keySpaceDef: KeySpaceDef = contactPoint.keySpace(domain.replace(".", "_"))

  applicationLifecycle.addStopHook(
    () => Future.successful {
      // com.websudos.phantom.Manager.shutdown()
      keySpaceDef.session.getCluster.close()
      keySpaceDef.session.close()
      Logger.info("Shutdown Phantom Cassandra Driver")
    }
  )

  // Basic Play Api
  implicit val basicPlayApi = BasicPlayApi(
    langs, messagesApi, environment, configuration, applicationLifecycle, actorSystem, materializer
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
        ReIndexInternalGroups(ig).start(),
        controllers.AccessControlsCtrl.initIfFirstRun,
        controllers.Layouts.initIfFirstRun
      )
    ).andThen { case Success(_) =>
      play.api.Logger.info("InternalGroups/AccessControls have been initialized with default data.")
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

  play.api.Logger.info("Models have been created.")

  // Actors
  startActors()

  // Error Handler
  override lazy val httpErrorHandler = new ErrorHandler(environment, configuration, sourceMapper, Some(router))

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

  play.api.Logger.info("Internal Controllers have been created.")

  // Internal Api Router
  val apiInternalRouter = new api_internal.Routes(
    httpErrorHandler,
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
    httpErrorHandler,
    userWebSocketCtrl
  )

  // Private Api Controllers
  val apiPrivatePingCtrl = new controllers.api_private.PingCtrl

  // Private Api Router
  val apiPrivateRouter = new api_private.Routes(
    httpErrorHandler,
    apiPrivatePingCtrl
  )

  // Permission Checking
  implicit val permCheckRequired =
    controllers.UserActionRequired(_groups, _accessControls)

  // Root Controllers
  val applicationCtrl    = new controllers.Application()
  val experimentalCtrl   = new controllers.ExperimentalCtrl()
  val fileSystemCtrl     = new controllers.FileSystemCtrl()
  val sessionsCtrl       = new controllers.SessionsCtrl(httpConfiguration, cookieSigner)
  val usersCtrl          = new controllers.UsersCtrl(httpConfiguration, cookieSigner)
  val myCtrl             = new controllers.MyCtrl(httpConfiguration, cookieSigner)
  val groupsCtrl         = new controllers.GroupsCtrl()
  val passwordResetCtrl  = new controllers.PasswordResetCtrl()
  val emailTemplatesCtrl = new controllers.EmailTemplatesCtrl()
  val accessControlsCtrl = new controllers.AccessControlsCtrl()

  play.api.Logger.info("Root Controllers have been created.")

  // Root Router
  lazy val router: Routes = new Routes(
    httpErrorHandler,
    apiInternalRouter,
    socketsRouter,
    apiPrivateRouter,
    applicationCtrl,
    experimentalCtrl,
    new controllers.Assets(httpErrorHandler),
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
      case "Compressor"                => new Compressor(GzipFilterConfig.fromConfiguration(configuration))
      case "HtmlCompressor"            => new HtmlCompressor(configuration, environment)
      case "RequestLogger"             => new RequestLogger()
      case "RequestTimeLogger"         => new RequestTimeLogger()
    }

  def startActors(): Unit = {
    actorSystem.actorOf(ResourcesManager.props, ResourcesManager.basicName)

    //Start Actor ShardRegion
    MailActor.startRegion(configuration, actorSystem)
    ChatActor.startRegion(configuration, actorSystem)
    NotificationActor.startRegion(configuration, actorSystem)

    play.api.Logger.info("Actor ShardRegions have been started.")
  }

  play.api.Logger.info("System has been started.")
}