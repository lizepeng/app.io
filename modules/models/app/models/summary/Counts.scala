package models.summary

import helpers._

/**
 * @author zepeng.li@gmail.com
 */
trait Counts[K <: EnumLike.Value, C <: Counts[K, C]] extends Any {
  this: C =>

  def self: Map[K, Int]

  def copyFrom: Map[K, Int] => C

  def +(kv: (K, Int)): C = copyFrom(
    self + (kv._1 -> (self.getOrElse(kv._1, 0) + kv._2))
  ).filter(_ != 0)

  def -(kv: (K, Int)): C = copyFrom(
    self + (kv._1 -> (self.getOrElse(kv._1, 0) - kv._2))
  ).filter(_ != 0)

  def ++(that: C): C = (this /: that.self) (_ + _)

  def --(that: C): C = (this /: that.self) (_ - _)

  def filter(p: Int => Boolean) = copyFrom(self.filter(entry => p(entry._2)))

  def +(kv: Option[(K, Int)]): C = {
    kv.map(this + _).getOrElse(this)
  }
}