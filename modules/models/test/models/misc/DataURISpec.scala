package models.misc

import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.libs.json.Json

@RunWith(classOf[JUnitRunner])
class DataURISpec extends Specification {

  val data = """data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg=="""

  "DataURI" should {

    "be able to parsed from string" >> {
      val parsed = DataURI.parse(data)
      parsed.get.toString mustEqual data
    }

    "be able to serialized to/deserialized from Json" >> {
      val json = s""""$data""""
      val dataURI = Json.fromJson[DataURI](Json.parse(json))
      val stringified = Json.stringify(Json.toJson(dataURI.get))
      stringified mustEqual json
    }
  }
}