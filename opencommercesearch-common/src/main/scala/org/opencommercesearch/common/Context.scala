package org.opencommercesearch.common

import play.api.i18n.Lang

import scala.collection.concurrent
import scala.collection.JavaConversions._

import java.util.concurrent.ConcurrentHashMap

/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

/**
 * The context for any artifacts with a preview and public context. These artifacts include search results, indexing and
 * content.
 *
 * @author rmerizalde
 */
sealed abstract class Context {

  /**
   * The context name
   */
  val name : String

  /**
   * The context lang
   */
  val lang : Lang

  /**
   * @return <code>true</code> if this is the public context. Otherwise return <code>false</false>
   */
  def isPublic : Boolean

  /**
   * @return <code>true</code> if this is the preview context. Otherwise return <code>false</false>
   */
  def isPreview : Boolean

}

object Context {
  private val publicContextCache: concurrent.Map[Lang, Context] = new ConcurrentHashMap[Lang, Context]()
  private val previewContextCache: concurrent.Map[Lang, Context] = new ConcurrentHashMap[Lang, Context]()

  def apply(preview: Boolean, lang: Lang) : Context = preview match {
    case true => previewContextCache.getOrElseUpdate(lang, new PreviewContext(lang))
    case false => publicContextCache.getOrElseUpdate(lang, new PublicContext(lang))
  }

  private case class PreviewContext(lang: Lang) extends Context {
    override val name = "Preview"

    def isPublic : Boolean = false

    def isPreview : Boolean = true
  }

  private case class PublicContext(lang: Lang) extends Context {
    override val name = "Public"

    def isPublic : Boolean = false

    def isPreview : Boolean = true
  }
}


