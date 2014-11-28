package org.opencommercesearch.api.models.debug

import org.apache.solr.common.util.NamedList
import play.api.libs.functional.syntax._
import play.api.libs.json._
import scala.collection.JavaConversions._
import scala.collection.mutable
import java.util
import org.opencommercesearch.api.util.JsUtils.PathAdditions

case class ScoreNode(
                      var value: Option[Float] = None,
                      var description: Option[String] = None,
                      var details: Option[Seq[ScoreNode]] = None) {

  def convertJavaResponse(debugMap: NamedList[AnyRef]) : Unit = {
    debugMap map { mapEntry =>
      if ("value".equals(mapEntry.getKey)) {
        if (mapEntry.getValue.isInstanceOf[Float]) {
            value = Option(mapEntry.getValue.asInstanceOf[Float])
        } else if (mapEntry.getValue.isInstanceOf[Double]) {
            value = Option(mapEntry.getValue.asInstanceOf[Double].toFloat)
        }
      } else if ("description".equals(mapEntry.getKey)) {
        description = Option(mapEntry.getValue.asInstanceOf[String])
      } else if ("details".equals(mapEntry.getKey)) {
        val scalaDetails = new mutable.ArrayBuffer[ScoreNode]
        val javaDetails = mapEntry.getValue.asInstanceOf[util.List[NamedList[AnyRef]]]
        javaDetails.foreach(entry => {
          val newScoreNode = new ScoreNode()
          newScoreNode.convertJavaResponse(entry)
          scalaDetails += newScoreNode
        })
        details = Option(scalaDetails.toSeq)
      }
    }
  }

}

object ScoreNode {

  implicit val readsRule : Reads[ScoreNode] = (
    (__ \ "value").readNullable[Float] ~
    (__ \ "description").readNullable[String] ~
    (__ \ "details").lazyReadNullable(Reads.list[ScoreNode](readsRule))
  ) (ScoreNode.apply _)

  implicit val writesRule : Writes[ScoreNode] = (
    (__ \ "value").writeNullable[Float] ~
    (__ \ "description").writeNullable[String] ~
    //Prevent empty details lists to be written
    (__ \ "details").lazyWriteNullableIterable[Seq[ScoreNode]](Writes.traversableWrites[ScoreNode](writesRule))
  )  (unlift(ScoreNode.unapply))

}
