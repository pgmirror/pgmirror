package com.github.irumiha.pgmirror

import java.sql.ResultSet

/**
 * A wrapper around ResultSet that makes it look like an Iterator[ResultSet]
 * Makes iterating and extracting values easier.
 *
 * @param rs The wrapped ResultSet
 */
class ResultSetIterator(val rs: ResultSet) extends Iterator[ResultSet] {
  private[this] var rowWasConsumed: Boolean = true
  private[this] var _hasNext: Boolean = false

  override def hasNext: Boolean = synchronized {
    if (rs.isClosed) false
    else {
      if (rowWasConsumed) {
        _hasNext = rs.next()
        rowWasConsumed = false
      }
      _hasNext
    }
  }

  override def next(): ResultSet = synchronized {
    rowWasConsumed = true
    rs
  }

  // Ideally the ResultSet will be closed outside this class, but this is
  // a convenience for playing in the REPL.
  def closeRs(): Unit = rs.close()
}

object ResultSetIterator {

  implicit class Rsi(val rs: ResultSet) extends AnyVal {
    def toIterator: ResultSetIterator = new ResultSetIterator(rs)
  }

}
