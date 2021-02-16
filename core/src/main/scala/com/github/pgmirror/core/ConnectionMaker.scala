package com.github.pgmirror.core

import java.sql.{Connection, DriverManager}

class ConnectionMaker {
  def doAll(driver: String, url: String, user: String, pass: String): Unit = {
    Class.forName(driver)

    val conn = DriverManager.getConnection(url, user, pass)

    import model.database._

    val allTables = Table.getTables(conn)

    allTables.foreach { t =>
      println(s"Analyzing table ${t.tableSchema}.${t.tableName}")
      val fks = ForeignKey.getForTable(t.tableSchema, t.tableName)(conn)

      fks.foreach(println)
    }

  }
}
