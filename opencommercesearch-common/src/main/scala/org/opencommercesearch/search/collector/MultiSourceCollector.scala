package org.opencommercesearch.search.collector

import org.opencommercesearch.search.Element
import scala.collection.mutable

/**
 * @author rmerizalde
 */
class MultiSourceCollector[E <: Element] extends MultiCollector[E] {

  val map = new mutable.HashMap[String, Collector[E]]

  override def canStop : Boolean = false

  override def isEmpty  : Boolean = collectors.collectFirst({ case c if !c.isEmpty => false }).getOrElse(true)

  override def add(element: E, source: String) : Boolean = {
    val collector = map.get(source)
    for (c <- collector) {
      c.add(element, source)
    }
    collector.isDefined
  }

  override def elements() : Seq[E] = collectors.foldLeft(new mutable.ArrayBuffer[E](size()))(_ ++= _.elements())

  override def size() : Int = collectors.foldLeft(0)(_ + _.size)

  override def capacity() : Int = collectors.foldLeft(0)(_ + _.capacity)

  override def collector(source: String): Option[Collector[E]] = map.get(source)

  override def collectors: Iterable[Collector[E]] = map.values

  override def sources: Iterable[String] = map.keySet

  override def add(source: String, collector: Collector[E]): Boolean = {
    if (source == null || collector == null || collector == this) {
      false
    } else {
      map.put(source, collector)
      true
    }
  }

}
