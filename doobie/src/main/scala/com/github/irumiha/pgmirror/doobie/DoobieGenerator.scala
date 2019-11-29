package com.github.irumiha.pgmirror.doobie

import com.github.irumiha.pgmirror.model.generator.{ForeignKey, TableLike}
import com.github.irumiha.pgmirror.{GeneratedFile, Generator, Settings}

class DoobieGenerator extends Generator {

  override def generateUtil(settings: Settings): Option[GeneratedFile] = None

  override def generateForTable(settings: Settings, table: TableLike, foreignKeys: List[ForeignKey]): List[GeneratedFile] = {
    val doobiePath = Seq(table.schemaName, "doobie").filterNot(_.isEmpty).mkString("/")

    val repository: String =
      if (table.isView) {
        generateViewRepository(settings, table)
      } else {
        generateTableRepository(settings, table)
      }

    List(
      GeneratedFile(table.schemaName, table.tableClassName+".scala", generateTableClass(settings, table)),
      GeneratedFile(doobiePath, table.tableClassName+"DoobieRepository.scala", repository)
    )
  }

  val tq = "\"\"\""

  def tablePackage(rootPackage: String, schemaName: String): String =
    List(rootPackage, schemaName).filterNot(_.isEmpty).mkString(".")

  def generateTableClass(settings: Settings, table: TableLike): String = {

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}
       |
       |import io.circe.Codec, io.circe.generic.semiauto.deriveCodec
       |
       |case class ${table.tableClassName} (
       |  ${table.columns.map(_.propWithComment).mkString(",\n  ")}
       |)
       |
       |object ${table.tableClassName} {
       |  implicit val ${table.tableClassName}Codec: Codec[${table.tableClassName}] = deriveCodec
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
         |      insert into ${table.tableWithSchema}
         |             (${table.columns.map(_.tableColumn).mkString(",\n              ")})
         |             values
         |             (${table.columns.map(tc => s"$${item.${tc.propName}}").mkString(",\n              ")})
         |      returning ${table.columns.map(_.tableColumn).mkString(",\n                ")}
         |    $tq
         |    .query[${table.tableClassName}]
         |    .unique
         |  }
         |""".stripMargin

    val getDef = pkColumn.map { p =>
      s"""
         |  def get(${p.prop}): ConnectionIO[Option[${table.tableClassName}]] = {
         |    sql$tq
         |      select ${table.columns.map(_.tableColumn).mkString(",\n             ")}
         |        from ${table.tableWithSchema}
         |       where ${p.tableColumn} = $$${p.propName}
         |    $tq
         |    .query[${table.tableClassName}]
         |    .option
         |  }
         |""".stripMargin
    }.getOrElse("")

    val deleteDef = pkColumn.map { p =>
      s"""
         |  def delete(${p.prop}): ConnectionIO[Option[${table.tableClassName}]] = {
         |    sql$tq
         |      delete from ${table.tableWithSchema}
         |       where ${p.tableColumn} = $$${p.propName}
         |      returning ${table.columns.map(_.tableColumn).mkString(",\n                ")}
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
         |      update ${table.tableWithSchema}
         |         set ${table.columns.filterNot(_.isPrimaryKey).map(tc => s"${tc.tableColumn} = $${item.${tc.propName}}").mkString(",\n             ")}
         |       where ${p.tableColumn} = $${item.${p.propName}}
         |      returning ${table.columns.map(_.tableColumn).mkString(",\n                ")}
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
       |$crudDefs
       |}
       |
       |""".stripMargin
  }

  def generateViewRepository(settings: Settings, view: TableLike): String = {
    import com.github.irumiha.pgmirror.model.generator.ColumnAnnotation._

    val filters: List[(String, String, String)] = for {
      col <- view.columns
      ann <- col.annotations
    } yield {
      val paramName = ann match {
        case FilterEq => s"${col.propName}_="
        case FilterLt => s"${col.propName}_<"
        case FilterGt => s"${col.propName}_>"
        case FilterGtEq => s"${col.propName}_>="
        case FilterLtEq => s"${col.propName}_<="
        case _ => throw new IllegalArgumentException("Only filter annotations allowed!")
      }

      val filterOp = ann match {
        case FilterEq => "="
        case FilterLt => "<"
        case FilterGt => ">"
        case FilterGtEq => ">="
        case FilterLtEq => "<="
        case _ => throw new IllegalArgumentException("Only filter annotations allowed!")
      }

      (
        s"$paramName: Option[${col.propType}],",
        s"""val ${paramName}Filter = $paramName.map(v => fr"${col.name} $filterOp $$v")""",
        s"${paramName}Filter"
      )
    }

    s"""
       |package ${tablePackage(settings.rootPackage, view.schemaName)}
       |
       |import doobie._
       |import doobie.implicits._
       |import doobie.postgres.implicits._
       |import Fragments.{ in, whereAndOpt }
       |
       |import java.util.UUID
       |import java.time.Instant
       |
       |class ${view.tableClassName}DoobieRepository {
       |  def listFiltered(
       |${filters.map(f => "      " + f._1).mkString("\n")}
       |      offset: Option[Int] = None,
       |      limit: Option[Int] = None,
       |  ): Query0[${view.tableClassName}] = {
       |${filters.map(f => "    " + f._2).mkString("\n")}
       |
       |    val q: Fragment =
       |      fr${tq}select ${view.columns.map(_.tableColumn).mkString(",")} from ${view.tableWithSchema}$tq ++
       |      whereAndOpt(${filters.map(f => f._3).mkString(",")}) ++
       |      if (offset.isDefined) Fragment.const(s"OFFSET $${offset.get}") else Fragment.empty ++
       |      if (limit.isDefined) Fragment.const(s"LIMIT $${limit.get}") else Fragment.empty
       |
       |    q.query[${view.tableClassName}]
       |}
       |
       |""".stripMargin
  }
}
