package controllers

import akka.NotUsed
import akka.stream.scaladsl.Source
import helpers._
import models.cfs.Block._
import models.cfs._
import play.api.libs.iteratee._
import play.api.libs.streams.Streams
import protocols._

import scala.language._

/**
 * @author zepeng.li@gmail.com
 */
trait CFSStreamComponents extends CFSDownloadComponents {
  self: DefaultPlayExecutor =>

  implicit def HttpRangeRequestableFile(file: File): HttpStreamable = new HttpStreamable {
    def size: Long = file.size
    def name: String = file.name
    def range(first: Long, length: Long): Source[BLK, NotUsed] =
      Source.fromPublisher(
        Streams.enumeratorToPublisher(
          file.read(first) &>
            Enumeratee.take(length.toInt) &>
            bandwidth.LimitTo(bandwidthConfig.stream)
        )
      )
    def whole: Source[BLK, NotUsed] =
      Source.fromPublisher(
        Streams.enumeratorToPublisher(
          file.read() &>
            bandwidth.LimitTo(bandwidthConfig.download)
        )
      )
  }
}