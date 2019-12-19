package com.github.irumiha.pgmirror.doobie

import com.github.irumiha.pgmirror.model.generator.{ForeignKey, TableLike}
import com.github.irumiha.pgmirror.{GeneratedFile, Generator, Settings}
import com.github.irumiha.pgmirror.model.generator.ColumnAnnotation._
import com.github.irumiha.pgmirror.model.generator.TableAnnotation.{Limit, Offset}

class DoobieGenerator extends Generator {

  override def generateUtil(settings: Settings): Option[GeneratedFile] = None

  override def generateForTable(settings: Settings, table: TableLike, foreignKeys: List[ForeignKey]): List[GeneratedFile] = {
    System.out.println(s"Processing: ${table.tableWithSchema}")
    val repositoryPath =
      if (table.isView)
        Seq(table.schemaName, "repositories", "doobie", "views").filterNot(_.isEmpty).mkString("/")
      else
        Seq(table.schemaName, "repositories", "doobie", "models").filterNot(_.isEmpty).mkString("/")

    val modelPath =
      if (table.isView)
        Seq(table.schemaName, "views").filterNot(_.isEmpty).mkString("/")
      else
        Seq(table.schemaName, "models").filterNot(_.isEmpty).mkString("/")

    val repository: String =
      if (table.isView)
        generateViewRepository(settings, table)
      else
        generateTableRepository(settings, table)

    List(
      GeneratedFile(modelPath, table.className + ".scala", generateTableClass(settings, table)),
      GeneratedFile(repositoryPath, table.className + "DoobieRepository.scala", repository)
    )
  }

  val tq = "\"\"\""

  def tablePackage(rootPackage: String, schemaName: String): String =
    List(rootPackage, schemaName).filterNot(_.isEmpty).mkString(".")

  def generateTableClass(settings: Settings, table: TableLike): String = {
    val packageSuffix =
      if (table.isView)
        "views"
      else
        "models"

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}.$packageSuffix
       |
       |import io.circe.Codec, io.circe.generic.semiauto.deriveCodec
       |
       |case class ${table.className} (
       |  ${table.columns.map(_.propWithComment).mkString(",\n  ")}
       |)
       |
       |object ${table.className} {
       |  implicit val ${table.className}Codec: Codec[${table.className}] = deriveCodec
       |}
       |
       |""".stripMargin
  }

  def generateTableRepository(settings: Settings, table: TableLike): String = {
    val pkColumn = table.columns.find(_.isPrimaryKey)

    val insertDef =
      s"""  def insert(item: ${table.className}): ConnectionIO[${table.className}] = {
         |    sql$tq
         |      insert into ${table.tableWithSchema}
         |             (${table.columns.map(_.columnNameQuoted).mkString(",\n              ")})
         |             values
         |             (${table.columns.map(tc => s"$${item.${tc.propName}}").mkString(",\n              ")})
         |      returning ${table.columns.map(_.tableColumn).mkString(",\n                ")}
         |    $tq
         |    .query[${table.className}]
         |    .unique
         |  }
         |""".stripMargin

    val getDef = pkColumn.map { p =>
      s"""  def get(${p.prop}): ConnectionIO[Option[${table.className}]] = {
         |    sql$tq
         |      select ${table.columns.map(_.columnNameQuoted).mkString(",\n             ")}
         |        from ${table.tableWithSchema}
         |       where ${p.tableColumn} = $$${p.propName}
         |    $tq
         |    .query[${table.className}]
         |    .option
         |  }
         |""".stripMargin
    }.getOrElse("")

    val finds = table.columns.filter(c => c.annotations.contains(Find)).map { c =>
      s"""  def findBy${c.propName.capitalize}(${c.prop}): ConnectionIO[List[${table.className}]] = {
         |    sql$tq
         |      select ${table.columns.map(_.columnNameQuoted).mkString(",\n             ")}
         |        from ${table.tableWithSchema}
         |       where ${c.tableColumn} = $$${c.propName}
         |    $tq
         |    .query[${table.className}]
         |    .to[List]
         |  }
         |""".stripMargin
    }

    val findOnes = table.columns.filter(c => c.annotations.contains(FindOne)).map { c =>
      s"""  def findOneBy${c.propName.capitalize}(${c.prop}): ConnectionIO[Option[${table.className}]] = {
         |    sql$tq
         |      select ${table.columns.map(_.columnNameQuoted).mkString(",\n             ")}
         |        from ${table.tableWithSchema}
         |       where ${c.tableColumn} = $$${c.propName}
         |    $tq
         |    .query[${table.className}]
         |    .option
         |  }
         |""".stripMargin
    }

    val deleteDef = pkColumn.map { p =>
      s"""  def delete(${p.prop}): ConnectionIO[Option[${table.className}]] = {
         |    sql$tq
         |      delete from ${table.tableWithSchema}
         |       where ${p.columnNameQuoted} = $$${p.propName}
         |      returning ${table.columns.map(_.columnNameQuoted).mkString(",\n                ")}
         |    $tq
         |    .query[${table.className}]
         |    .option
         |  }
         |""".stripMargin
    }.getOrElse("")


    val updateDef = pkColumn.map { p =>
      s"""  def update(item: ${table.className}): ConnectionIO[Option[${table.className}]] = {
         |    sql$tq
         |      update ${table.tableWithSchema}
         |         set ${table.columns.filterNot(_.isPrimaryKey).map(tc => s"${tc.columnNameQuoted} = $${item.${tc.propName}}").mkString(",\n             ")}
         |       where ${p.tableColumn} = $${item.${p.propName}}
         |      returning ${table.columns.map(_.columnNameQuoted).mkString(",\n                ")}
         |    $tq
         |    .query[${table.className}]
         |    .option
         |  }
         |""".stripMargin
    }.getOrElse("")

    val crudDefs =
      s"""$insertDef
         |$getDef
         |$deleteDef
         |$updateDef
         |${finds.mkString("\n")}
         |${findOnes.mkString("\n")}
         |""".stripMargin

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}.repositories.doobie.models
       |
       |import doobie._
       |import doobie.implicits._
       |import doobie.postgres.implicits._
       |
       |import java.util.UUID
       |import java.time.Instant
       |
       |import ${tablePackage(settings.rootPackage, table.schemaName)}.models.${table.className}
       |
       |class ${table.className}DoobieRepository {
       |$crudDefs
       |}
       |
       |""".stripMargin
  }

  def generateViewRepository(settings: Settings, view: TableLike): String = {

    val filters: List[(String, String, String)] = for {
      col <- view.columns
      ann <- col.annotations.filterNot(_ == NotNull)
    } yield {
      val (paramName, paramNameVal) = ann match {
        case FilterEq   => (s"${col.propName}_=", s"${col.propName}")
        case FilterLt   => (s"${col.propName}_<", s"${col.propName}")
        case FilterGt   => (s"${col.propName}_>", s"${col.propName}")
        case FilterGtEq => (s"${col.propName}_>=", s"${col.propName}")
        case FilterLtEq => (s"${col.propName}_<=", s"${col.propName}")
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
        s"$paramName : Option[${col.propType}] = None,",
        s"""val ${paramNameVal}Filter = $paramName.map(v => fr"${col.name} $filterOp $$v")""",
        s"${paramNameVal}Filter"
      )
    }
    val (limitParam, limitFr, rLimitFr) =
      if (view.annotations.contains(Limit))
        (
          "      limit: Option[Int] = None,\n",
          "    val limitFr: Fragment = if (limit.isDefined) Fragment.const(s\"LIMIT $${limit.get}\") else Fragment.empty\n",
          " ++ limitFr"
        )
      else
        ("", "", "")

    val (offsetParam, offsetFr, rOffsetFr) =
      if (view.annotations.contains(Offset))
        (
          "      offset: Option[Int] = None\n",
          "    val offsetFr: Fragment = if (offset.isDefined) Fragment.const(s\"OFFSET $${offset.get}\") else Fragment.empty\n",
          " ++ offsetFr"
        )
      else
        ("", "", "")

    val (whereFr, rWhereFr) =
      if (filters.nonEmpty)
        (
          s"    val whereFr: Fragment = whereAndOpt(${filters.map(f => f._3).mkString(",")})\n",
          " ++ whereFr"
        )
      else
        ("", "")

    s"""
       |package ${tablePackage(settings.rootPackage, view.schemaName)}.repositories.doobie.views
       |
       |import doobie._
       |import doobie.implicits._
       |import doobie.postgres.implicits._
       |import Fragments.{ in, whereAndOpt }
       |
       |import java.util.UUID
       |import java.time.Instant
       |
       |import ${tablePackage(settings.rootPackage, view.schemaName)}.views.${view.className}
       |
       |class ${view.className}DoobieRepository {
       |  def listFiltered(${filters.map(f => "      " + f._1).mkString("\n","\n","\n")}$offsetParam$limitParam  ): Query0[${view.className}] = {
       |${filters.map(f => "    " + f._2).mkString("\n")}
       |    val selectFr: Fragment =
       |      fr${tq}select ${view.columns.map(_.tableColumn).mkString(",")} from ${view.tableWithSchema}$tq
       |$whereFr$offsetFr$limitFr
       |    val q: Fragment = selectFr${rWhereFr}${rLimitFr}${rOffsetFr}
       |
       |    q.query[${view.className}]
       |  }
       |}
       |
       |""".stripMargin
  }
}
