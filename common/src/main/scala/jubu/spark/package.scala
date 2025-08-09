package jubu

package object spark {
  /**
   * Splits a string into a sequence of lowercased words.
   */
  val splitter: String => Seq[String]  = { p:String =>
    p.split("\\s+").map(_.toLowerCase).toSeq
  }
}