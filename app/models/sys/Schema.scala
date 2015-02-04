package models.sys

import models.cassandra.Cassandra
import models.cfs._
import models.{User, UserByEmail}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object Schema extends Cassandra {

  def create = {
    Await.result(User.create.future(), 500 millis)
    Await.result(UserByEmail.create.future(), 500 millis)
    Await.result(INode.create.future(), 500 millis)
    Await.result(IndirectBlock.create.future(), 500 millis)
    Await.result(Block.create.future(), 500 millis)
    Await.result(SysConfig.create.future(), 500 millis)
  }

}