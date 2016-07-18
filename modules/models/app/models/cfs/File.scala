package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.dsl._
import helpers.ExtMap._
import helpers._
import models.cassandra._
import models.cfs.Block.BLK
import models.cfs.CassandraFileSystem._
import play.api.libs.iteratee._
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * @author zepeng.li@gmail.com
 */
case class File(
  name: String,
  path: Path,
  owner_id: UUID,
  parent: UUID,
  id: UUID = UUIDs.timeBased(),
  size: Long = 0,
  indirect_block_size: Int = 1024 * 32 * 1024 * 8,
  block_size: Int = 1024 * 512,
  permission: Permission = Role.owner.rw,
  ext_permission: ExtPermission = ExtPermission(),
  attributes: Map[String, String] = Map(),
  is_directory: Boolean = false
) extends INode {

  def read(offset: Long = 0)(
    implicit cfs: CassandraFileSystem
  ): Enumerator[BLK] =
    if (offset == 0) cfs._indirectBlocks.read(id)
    else cfs._indirectBlocks.read(this, offset)

  def save(ttl: Duration = Duration.Inf)(
    implicit cfs: CassandraFileSystem
  ): Iteratee[BLK, File] =
    cfs._files.streamWriter(this, ttl)

  override def delete()(
    implicit cfs: CassandraFileSystem
  ): Future[Unit] =
    for {
      _ <- cfs._files.purge(id)
      _ <- super.delete()
    } yield Unit
}

/**
 *
 */
sealed class FileTable
  extends NamedCassandraTable[FileTable, File]
    with INodeCanonicalNamed
    with INodeKey[FileTable, File]
    with INodeColumns[FileTable, File]
    with FileColumns[FileTable, File] {

  override def fromRow(r: Row): File = {
    File(
      "",
      Path(),
      owner_id(r),
      parent(r),
      inode_id(r),
      size(r),
      indirect_block_size(r),
      block_size(r),
      Permission(permission(r)),
      ExtPermission(ext_permission(r).mapValuesSafely(Permission(_))),
      attributes(r),
      is_directory(r)
    )
  }
}

object File extends CanonicalNamed with ExceptionDefining {

  override val basicName: String = "file"

  case class NotFound(id: UUID)
    extends BaseException(error_code("not.found"))

  case class NotFile(param: Any)
    extends BaseException(error_code("not.file"))

  implicit val jsonWrites = new Writes[File] {
    override def writes(o: File): JsValue = Json.obj(
      "id" -> o.id,
      "name" -> o.name,
      "path" -> o.path,
      "size" -> o.size,
      "owner_id" -> o.owner_id,
      "created_at" -> o.created_at,
      "permission" -> o.permission.toBitSet.toIndices,
      "is_directory" -> o.is_directory,
      "is_file" -> !o.is_directory
    )
  }

  def pprint(size: Long): String = {
    size match {
      case s if s > (1L << 40) => f"${s / 1e12}%7.3f TB"
      case s if s > (1L << 30) => f"${s / 1e09}%6.2f GB"
      case s if s > (1L << 20) => f"${s / 1e06}%5.1f MB"
      case s if s > (1L << 10) => f"${s / 1000}%3d KB"
      case s if s > 0          => f"$s%3d bytes"
      case _                   => "Zero bytes"
    }
  }
}

class Files(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef,
  val _blocks: Blocks,
  val _indirectBlocks: IndirectBlocks
) extends FileTable
  with ExtCQL[FileTable, File]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  def find(id: UUID)(onFound: File => File): Future[File] = CQL {
    select.where(_.inode_id eqs id)
  }.one().recover {
    case e: Throwable => Logger.trace(e.getMessage); None
  }.map {
    case None                      => throw File.NotFound(id)
    case Some(f) if f.is_directory => throw File.NotFile(id)
    case Some(f)                   => onFound(f)
  }

  def streamWriter(
    inode: File, ttl: Duration = Duration.Inf
  ): Iteratee[BLK, File] = {
    Enumeratee.grouped[BLK] {
      Traversable.take[BLK](inode.block_size) &>>
        Iteratee.consume[BLK]()
    } &>>
      Iteratee.foldM[BLK, IndirectBlock](new IndirectBlock(inode.id)) {
        (curr, blk) =>
          for {
            ____ <- _blocks.write(curr.id, curr.length, blk, ttl)
            temp <- Future.successful(curr + blk.length)
            next <- temp.length < inode.indirect_block_size match {
              case true  => Future.successful(temp)
              case false => _indirectBlocks.write(temp, ttl).map(_.next)
            }
          } yield next
      }.mapM { last =>
        for {
          ____ <- {
            if (last.length != 0) _indirectBlocks.write(last, ttl)
            else Future.successful(last)
          }
          file <- this.write(inode.copy(size = last.offset + last.length), ttl)
        } yield file
      }

  }

  def purge(id: UUID): Future[Unit] = for {
    _____ <- _indirectBlocks.purge(id)
    empty <- _indirectBlocks.isEmpty(id)
    _____ <- if (empty) CQL {delete.where(_.inode_id eqs id)}.future() else purge(id)
  } yield Unit


  private def write(f: File, ttl: Duration = Duration.Inf): Future[File] = {
    val cql =
      insert.value(_.inode_id, f.id)
        .value(_.parent, f.parent)
        .value(_.is_directory, false)
        .value(_.size, f.size)
        .value(_.indirect_block_size, f.indirect_block_size)
        .value(_.block_size, f.block_size)
        .value(_.owner_id, f.owner_id)
        .value(_.permission, f.permission.self)
        .value(_.ext_permission, f.ext_permission.self.mapValuesSafely(_.self.toInt))
        .value(_.attributes, f.attributes)
    (ttl match {
      case t: FiniteDuration => CQL {cql.ttl(t)}
      case _                 => CQL {cql}
    }).future().map(_ => f)
  }
}