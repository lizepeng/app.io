package messages

import java.util.UUID

/**
 * Class for sending to a user, addressing be user id.
 *
 * @param uid The receiver of this envelope.
 * @param content The content in this envelope.
 * @tparam T type of content
 *
 * @author zepeng.li@gmail.com
 */
case class Envelope[T](
  uid: UUID,
  content: T
)