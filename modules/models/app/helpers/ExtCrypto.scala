package helpers

import java.security.MessageDigest

import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object ExtCrypto {

  def sha2(text: String, length: Int = 256): String = {
    val digest = MessageDigest.getInstance(s"SHA-$length")
    digest.reset()
    digest.update(text.getBytes)
    digest.digest().map(0xFF &) //convert to unsigned int
      .map {"%02x".format(_)}.foldLeft("") {_ + _}
  }
}