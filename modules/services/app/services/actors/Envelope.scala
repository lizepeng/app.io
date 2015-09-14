package services.actors

import java.util.UUID

/**
 * Envelope for sending payload to a entity with a UUID.
 *
 * @param id The receiver of the envelope.
 * @param payload The payload in this envelope.
 * @tparam T type of content
 *
 * @author zepeng.li@gmail.com
 */
case class Envelope[T](id: UUID, payload: T)