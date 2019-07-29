package com.github.irumiha.pgmirror.doobie

import com.github.irumiha.pgmirror.model.generator.{Column, ForeignKey, TableLike}
import com.github.irumiha.pgmirror.{GeneratedFile, Generator, Settings}

class DoobieGenerator extends Generator {

  override def generateUtil(settings: Settings): Option[GeneratedFile] = None

  override def generateForTable(settings: Settings, table: TableLike, foreignKeys: List[ForeignKey]): List[GeneratedFile] = {
    val doobiePath = Seq(table.schemaName, "doobie").filterNot(_.isEmpty).mkString("/")

    List(
      GeneratedFile(table.schemaName, table.tableClassName+".scala", generateTableClass(settings, table)),
      GeneratedFile(doobiePath, table.tableClassName+"DoobieRepository.scala", generateTableRepository(settings, table))
    )
  }

  val tq = "\"\"\""

  def propType(c: Column): String = {
    if (c.isNullable) {
      s"""Option[${c.modelType}]"""
    } else {
      s"""${c.modelType}"""
    }
  }

  def propName(c: Column): String = {
    val nameParts = c.name.split("_")
    nameParts.head + nameParts.tail.map(_.capitalize).mkString
  }

  def prop(c: Column): String = s"""${propName(c)}: ${propType(c)}"""

  def propWithComment(c: Column): String = s"""${c.comment.map(co => s"// $co\n|  ").getOrElse("")}${propName(c)}: ${propType(c)}"""

  def tablePackage(rootPackage: String, schemaName: String) = List(rootPackage, schemaName).filterNot(_.isEmpty).mkString(".")

  def tableColumn(c: Column): String = s""""${c.tableName}"."${c.name}""""

  def tableWithSchema(table: TableLike) = List(table.schemaName, s""""${table.tableName}"""").filterNot(_.isEmpty).mkString(".")

  def generateTableClass(settings: Settings, table: TableLike): String = {

    val circeEncodersDecoders =
      s"""  implicit val jsonEncoder = deriveEncoder[${table.tableClassName}]
         |  implicit val jsonDecoder = deriveDecoder[${table.tableClassName}]""".stripMargin

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}
       |
       |import io.circe.java8.time._
       |import io.circe.generic.semiauto._
       |
       |case class ${table.tableClassName} (
       |  ${table.columns.map(propWithComment).mkString(",\n  ")}
       |)
       |
       |object ${table.tableClassName} {
       |$circeEncodersDecoders
       |}
       |
       |""".stripMargin
  }

  def generateTableRepository(settings: Settings, table: TableLike): String = {
    val pkColumn = table.columns.find(_.isPrimaryKey)

    val insertDef =
      s"""
         |  def insert(item: ${table.tableClassName}): ConnectionIO[${table.tableClassName}] = {
         |    sql$tq
         |      insert into ${tableWithSchema(table)}
         |             (${table.columns.map(tableColumn).mkString(",\n              ")})
         |             values
         |             (${table.columns.map(tc => s"$${item.${propName(tc)}}").mkString(",\n              ")})
         |      returning ${table.columns.map(tableColumn).mkString(",\n                ")}
         |    $tq
         |    .query[${table.tableClassName}]
         |    .unique
         |  }
         |""".stripMargin

    val getDef = pkColumn.map { p =>
      s"""
         |  def get(${prop(p)}): ConnectionIO[Option[${table.tableClassName}]] = {
         |    sql$tq
         |      select ${table.columns.map(tableColumn).mkString(",\n             ")}
         |        from ${tableWithSchema(table)}
         |       where ${tableColumn(p)} = $$${propName(p)}
         |    $tq
         |    .query[${table.tableClassName}]
         |    .option
         |  }
         |""".stripMargin
    }.getOrElse("")

    val deleteDef = pkColumn.map { p =>
      s"""
         |  def delete(${prop(p)}): ConnectionIO[Option[${table.tableClassName}]] = {
         |    sql$tq
         |      delete from ${tableWithSchema(table)}
         |       where ${tableColumn(p)} = $$${propName(p)}
         |      returning ${table.columns.map(tableColumn).mkString(",\n                ")}
         |    $tq
         |    .query[${table.tableClassName}]
         |    .option
         |  }
         |""".stripMargin
    }.getOrElse("")


    val updateDef = pkColumn.map { p =>
      s"""
         |  def update(item: ${table.tableClassName}): ConnectionIO[Option[${table.tableClassName}]] = {
         |    sql$tq
         |      update ${tableWithSchema(table)}
         |         set ${table.columns.filterNot(_.isPrimaryKey).map(tc => s"${tableColumn(tc)} = $${item.${propName(tc)}}").mkString(",\n             ")}
         |       where ${tableColumn(p)} = $${item.${propName(p)}}
         |      returning ${table.columns.map(tableColumn).mkString(",\n                ")}
         |    $tq
         |    .query[${table.tableClassName}]
         |    .option
         |
         |  }
         |""".stripMargin
    }.getOrElse("")

    val crudDefs =
      s"""$insertDef
         |$getDef
         |$deleteDef
         |$updateDef
         |""".stripMargin

    val viewDefs =
      s""""""

    s"""
       |package ${tablePackage(settings.rootPackage, table.schemaName)}
       |
       |import doobie._
       |import doobie.implicits._
       |import doobie.postgres.implicits._
       |
       |import java.util.UUID
       |import java.time.Instant
       |
       |class ${table.tableClassName}DoobieRepository {
       |${if(table.isView) viewDefs else crudDefs}
       |}
       |
       |""".stripMargin
  }
}
