package services.actors

/**
 * Envelope for sending payload to a entity with a UUID.
 *
 * @param id      The receiver of the envelope.
 * @param payload The payload in this envelope.
 * @tparam ID type of id
 * @tparam PL type of content
 * @author zepeng.li@gmail.com
 */
case class Envelope[ID, PL](id: ID, payload: PL)