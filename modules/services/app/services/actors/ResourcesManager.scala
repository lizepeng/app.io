package services.actors

import akka.actor._
import com.websudos.phantom.connectors.KeySpaceDef
import elasticsearch.ElasticSearch
import helpers.BasicPlayApi
import models._
import services._

/**
 * @author zepeng.li@gmail.com
 */
object ResourcesManager extends CanonicalNamedActor {

  override val basicName = "resources_manager"

  def props(
    implicit
    basicPlayApi: BasicPlayApi,
    keySpaceDef: KeySpaceDef,
    elasticSearch: ElasticSearch,
    mailService: MailService,
    _chatHistories: ChatHistories,
    _mailInbox: MailInbox,
    _mailSent: MailSent
  ): Props = Props(new ResourcesManager)

  case object GetBasicPlayApi
  case object GetKeySpaceDef
}

class ResourcesManager(
  implicit
  basicPlayApi: BasicPlayApi,
  keySpaceDef: KeySpaceDef,
  elasticSearch: ElasticSearch,
  mailService: MailService,
  _chatHistories: ChatHistories,
  _mailInbox: MailInbox,
  _mailSent: MailSent
) extends Actor
  with ActorLogging {

  override def preStart() = {
    log.info("Started")
  }

  import ResourcesManager._

  def receive = {

    case keys: List[Any] => sender ! keys.collect {
      case GetBasicPlayApi => basicPlayApi
      case GetKeySpaceDef  => keySpaceDef
      case ElasticSearch   => elasticSearch
      case MailService     => mailService
      case ChatHistory     => _chatHistories
      case MailInbox       => _mailInbox
      case MailSent        => _mailSent
    }
  }
}