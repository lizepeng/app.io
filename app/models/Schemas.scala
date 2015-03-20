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
    Seq(
      SysConfig.create.future(),
      User.create.future(),
      UserByEmail.create.future(),
      INode.create.future(),
      IndirectBlock.create.future(),
      Block.create.future(),
      AccessControl.create.future(),
      ExpirableLink.create.future()
    )
  )
}