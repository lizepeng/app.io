package models

import com.datastax.driver.core.ResultSet
import models.cassandra.Cassandra
import models.cfs._
import models.sys.SysConfig
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object Schemas extends Cassandra {

  def create: Future[Seq[ResultSet]] = Future.sequence(
    //TODO
    Seq(
      //System
      SysConfig.create.ifNotExists.future(),
      AccessControl.create.ifNotExists.future(),
      SessionData.create.ifNotExists.future(),
      RateLimits.create.ifNotExists.future(),

      //User
      User.create.ifNotExists.future(),
      UserByEmailIndex.create.ifNotExists.future(),
      Group.create.ifNotExists.future(),
      Person.create.ifNotExists.future(),

      //CFS
      INode.create.ifNotExists.future(),
      IndirectBlock.create.ifNotExists.future(),
      Block.create.ifNotExists.future(),

      ExpirableLink.create.ifNotExists.future(),
      EmailTemplate.create.ifNotExists.future()
    )
  )
}