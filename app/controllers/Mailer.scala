package controllers

import java.io.File

import org.apache.commons.mail.EmailAttachment
import play.api.Play.current
import play.api.libs.mailer._
import play.api.mvc.{Action, Controller}

/**
 * @author zepeng.li@gmail.com
 */
object Mailer extends Controller {
  def send = Action {
    val email = Email(
      "Simple email",
      "Mister FROM <from@email.com>",
      Seq("Miss TO <zepeng.li@gmail.com>"),
      attachments = Seq(
        AttachmentFile("favicon.png", new File(current.classloader.getResource("public/javascripts/hello.js").getPath)),
        AttachmentData("data.txt", "data".getBytes, "text/plain", Some("Simple data"), Some(EmailAttachment.INLINE))
      ),
      bodyText = Some("A text message"),
      bodyHtml = Some("<html><body><p>An <b>html</b> message</p></body></html>")
    )
    val id = MailerPlugin.send(email)
    Ok(s"Email $id sent!")
  }
}