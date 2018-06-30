package com.reroute.backend.utils.postgres

import java.sql.ResultSet

/**
 * Wraps a JDBC result set into a Scala iterator.
 *
 * @param rs      the backing JDBC result set
 * @param mapping the function to use to extract data from each result set row
 * @tparam T the output type of mapping
 */
class ResultSetIterator[T](val rs: ResultSet, val mapping: ResultSet => T) extends Iterator[T] {

  override def hasNext: Boolean = {
    !isRsEmpty && !rs.isLast
  }

  override def next(): T = {
    rs.next()
    mapping(rs)
  }

  //If rs.getRow is not 0 then we already know we have data.
  //If rs.isBeforeFirst is false when we are at 0 then we dont have a row 1, or any other data.
  private val isRsEmpty = rs.getRow <= 0 && !rs.isBeforeFirst

}
