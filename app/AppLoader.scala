import controllers.{Application, _}
import helpers.BasicPlayApi
import messages.ChatActor
import models.{InternalGroups, Schemas}
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

  val basicPlayApi  = BasicPlayApi(langs, messagesApi, configuration)
  val bandwidth     = BandwidthService(basicPlayApi, actorSystem)
  val mailService   = MailService(basicPlayApi, actorSystem)
  val elasticSearch = elasticsearch.ElasticSearch(basicPlayApi)
  val secured       = buildSecured
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
      new api.Users(basicPlayApi, elasticSearch).dropIndexIfEmpty,
      new api.Groups(basicPlayApi, elasticSearch).dropIndexIfEmpty,
      new api.AccessControls(basicPlayApi, elasticSearch).dropIndexIfEmpty,
      InternalGroups.initialize.flatMap { done =>
        if (done) new api.Groups(basicPlayApi, elasticSearch).reindex
        else Future.successful(false)
      },
      Groups.initialize,
      AccessControls.initialize
    )
  ).onSuccess {
    case _ => Logger.info("System has started")
  }

  private def buildSecured: RegisteredSecured = {
    new RegisteredSecured(
      messagesApi,
      Seq(
        Files,
        Groups,
        Users,
        EmailTemplates,
        AccessControls,
        controllers.api.Groups,
        controllers.api.Users,
        controllers.api.Search,
        controllers.api.Files,
        controllers.api.AccessControls
      )
    )
  }

  private def buildApiRouter = new _root_.api.Routes(
    httpErrorHandler,
    new api.Search(basicPlayApi, elasticSearch),
    new api.Groups(basicPlayApi, elasticSearch),
    new api.Users(basicPlayApi, elasticSearch),
    new api.AccessControls(basicPlayApi, elasticSearch),
    new api.Files(basicPlayApi, bandwidth)
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
    new Chat(basicPlayApi),
    new Assets(httpErrorHandler),
    new Files(basicPlayApi),
    new Sessions(basicPlayApi),
    new Users(basicPlayApi, elasticSearch),
    new My(basicPlayApi, elasticSearch),
    new Groups(basicPlayApi),
    new PasswordReset(basicPlayApi)(mailService),
    new EmailTemplates(basicPlayApi),
    new AccessControls(basicPlayApi)(secured)
  )

}

class MyComponent(messages: MessagesApi) {

}