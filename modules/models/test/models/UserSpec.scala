package models

import com.datastax.driver.core.utils._
import models.misc.EmailAddress
import models.sys.SysConfigs
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._

import scala.concurrent._
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class UserSpec extends Specification with EmbeddedCassandra {

  "C* User" should {

    val user = User(UUIDs.timeBased(), email = EmailAddress("a@a.a"))

    "be able to save and load [[User]]" in {
      implicit val sysConfig = new SysConfigs
      implicit val _groups = new InternalGroups(
        _ => Future(Unit),
        _ => Future(Unit)
      )
      implicit val _users = new Users

      val future = for {
        _ <- _users.save(user)
        u <- _users.find(user.id)
      } yield u

      val ret = Await.result(future, 5.seconds)
      ret.internal_group_bits mustEqual InternalGroup.Anyone.toBits
      ret.internal_groups mustEqual _groups.find(InternalGroup.Anyone.toBits)

      val future1 = for {
        u1 <- user.addGroup(InternalGroup(1))
        u2 <- _users.find(user.id)
        __ <- user.preferences + ("layout" -> List("Admin", "User"))
        __ <- user.preferences + ("page_size" -> 100)
        __ <- user.preferences + ("page_size" -> 200)
        __ <- user.preferences + ("page_num" -> 2)
        __ <- user.preferences - "page_num"
        l1 <- user.preferences[List[String]]("layout")
        p1 <- user.preferences[Int]("page_size")
        p2 <- user.preferences[Int]("page_num")
      } yield (u1, u2, l1, p1, p2)

      val (u1, u2, l1, p1, p2) = Await.result(future1, 5.seconds)
      u1.internal_group_bits mustEqual (InternalGroup.Anyone | InternalGroup(1))
      u2.internal_group_bits mustEqual (InternalGroup.Anyone | InternalGroup(1))
      u1.internal_groups mustEqual _groups.InternalGroupIds.take(2)
      u2.internal_groups mustEqual _groups.InternalGroupIds.take(2)
      l1 mustEqual Some(List("Admin", "User"))
      p1 mustEqual Some(200)
      p2 mustEqual None
    }
  }
}