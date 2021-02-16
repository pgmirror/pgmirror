package com.github.pgmirror.doobie

import com.github.pgmirror.core.model.generator.ColumnAnnotation._
import com.github.pgmirror.core.model.generator.TableAnnotation._
import com.github.pgmirror.core.model.generator._
import com.github.pgmirror.core.{GeneratedFile, Generator, Names, Settings}

import java.io.File

class DoobieGenerator(settings: Settings) extends Generator(settings) {

  private val tq = "\"\"\""

  override def generateUtil: List[GeneratedFile] =
    List(
      GeneratedFile("repository", "DoobieRepository.scala", generateBaseRepository())
    )

  private def generateBaseRepository(): String =
    s"""package ${settings.rootPackage}.repository
      |
      |import doobie._
      |import io.circe.{Decoder, Encoder}
      |import doobie.Read
      |
      |
      |abstract class DoobieRepository[E: Read : Encoder: Decoder, PK] {
      |  def tableColumns: List[String]
      |  val hasDefault: List[E => Boolean]
      |
      |  protected def columnsToInsert(item: E): List[String] =
      |    tableColumns
      |      .zip(hasDefault)
      |      .filterNot{ case (_, p) => p(item)}
      |      .map{ case (col, _) => col}
      |
      |  def insertSql(item: E): Fragment
      |  def insertAllSql(item: E): Fragment
      |  def getSql(pk: PK): Fragment
      |  def deleteSql(pk: PK): Fragment
      |  def updateSql(item: E): Fragment
      |
      |  def insertQuery(item: E): Query0[E] = insertSql(item).query[E]
      |  def insertAllQuery(item: E): Query0[E] = insertAllSql(item).query[E]
      |  def getQuery(pk: PK): Query0[E] = getSql(pk).query[E]
      |  def deleteQuery(pk: PK): Query0[E] = deleteSql(pk).query[E]
      |  def updateQuery(item: E): Query0[E] = updateSql(item).query[E]
      |
      |  def insert(item: E): ConnectionIO[E] =
      |    insertQuery(item).unique
      |
      |  def insertAllValues(item: E): ConnectionIO[E] =
      |    insertAllQuery(item).unique
      |
      |  def get(pk: PK): ConnectionIO[Option[E]] =
      |    getQuery(pk).option
      |
      |  def delete(pk: PK): ConnectionIO[Option[E]] =
      |    deleteQuery(pk).option
      |
      |  def update(item: E): ConnectionIO[Option[E]] =
      |    updateQuery(item).option
      |}
      |""".stripMargin

  override def generateForTable(
    table: Table,
    foreignKeys: List[ForeignKey],
  ): List[GeneratedFile] = {
    System.out.println(s"Processing table: ${table.nameWithSchema}")
    val (repositoryPath: String, modelPath: String) = paths(table)

    val repository: String =
      generateTableRepository(settings, table)

    //noinspection DuplicatedCode
    List(
      GeneratedFile(
        modelPath,
        Names.toClassCamelCase(table.name) + ".scala",
        generateDataClass(settings, table),
      ),
      GeneratedFile(repositoryPath, Names.toClassCamelCase(table.name) + "Repository.scala", repository),
    )
  }

  private def generateTableRepository(settings: Settings, table: Table): String = {
    val pkColumn = table.columns.find(_.isPrimaryKey)

    val insertDef =
      s"""  override def insertSql(item: ${Names.toClassCamelCase(table.name)}): Fragment = {
         |    val insertInto =
         |      Fragment.const(
         |        columnsToInsert(item).mkString(${tq}insert into ${table.nameWithSchema} ($tq,", ", ") ")
         |      )
         |
         |    val values =
         |      Fragment.const(
         |        columnsToInsert(item).mkString("(select ", ", ", " ")
         |      )
         |
         |    val subselect =
         |      fr${tq}from (SELECT (___inner::${table.nameWithSchema}).* from (select ${table.columns.map(tc => s"$${item.${tc.scalaPropName}}").mkString(",")}) as ___inner) as __outer)$tq
         |
         |    val returning = fr${tq}returning ${table.columns
           .map(_.tableColumn)
           .mkString(",")}$tq
         |
         |    insertInto ++ values ++ subselect ++ returning
         |  }
         |""".stripMargin

    val insertAllDef =
      s"""  override def insertAllSql(item: ${Names.toClassCamelCase(table.name)}): Fragment =
         |    sql$tq
         |      insert into ${table.nameWithSchema}
         |             (${table.columns.map(_.columnNameQuoted).mkString(",\n              ")})
         |             values
         |             (${table.columns.map(tc => s"$${item.${tc.scalaPropName}}").mkString(",\n              ")})
         |      returning ${table.columns.map(_.tableColumn).mkString(",\n                ")}
         |    $tq
         |""".stripMargin

    val getDef = pkColumn
      .map { p =>
        s"""  override def getSql(${p.scalaProp}): Fragment =
         |    sql$tq
         |      select ${table.columns.map(_.columnNameQuoted).mkString(",\n             ")}
         |        from ${table.nameWithSchema}
         |       where ${p.tableColumn} = $$${p.scalaPropName}
         |    $tq
         |""".stripMargin
      }
      .getOrElse("")

    val finds = table.columns.filter(c => c.annotations.contains(Find)).map { c =>
      s"""  def findBy${c.scalaPropName.capitalize}Sql(${c.scalaProp}): Fragment =
         |    sql$tq
         |      select ${table.columns.map(_.columnNameQuoted).mkString(",\n             ")}
         |        from ${table.nameWithSchema}
         |       where ${c.tableColumn} = $$${c.scalaPropName}
         |    $tq
         |
         |  def findBy${c.scalaPropName.capitalize}(${c.scalaProp}): ConnectionIO[List[${Names.toClassCamelCase(table.name)}]] = {
         |    findBy${c.scalaPropName.capitalize}Sql(${c.scalaPropName})
         |    .query[${Names.toClassCamelCase(table.name)}]
         |    .to[List]
         |  }
         |""".stripMargin
    }

    val findOnes = table.columns.filter(c => c.annotations.contains(FindOne)).map { c =>
      s"""  def findOneBy${c.scalaPropName.capitalize}Sql(${c.scalaProp}): Fragment =
         |    sql$tq
         |      select ${table.columns.map(_.columnNameQuoted).mkString(",\n             ")}
         |        from ${table.nameWithSchema}
         |       where ${c.tableColumn} = $$${c.scalaPropName}
         |    $tq
         |
         |  def findOneBy${c.scalaPropName.capitalize}(${c.scalaProp}): ConnectionIO[Option[${Names.toClassCamelCase(table.name)}]] = {
         |    findOneBy${c.scalaPropName.capitalize}Sql(${c.scalaPropName}).query[${Names.toClassCamelCase(table.name)}]
         |    .option
         |  }
         |""".stripMargin
    }

    val deleteDef = pkColumn
      .map { p =>
        s"""  override def deleteSql(${p.scalaProp}): Fragment =
         |    sql$tq
         |      delete from ${table.nameWithSchema}
         |       where ${p.columnNameQuoted} = $$${p.scalaPropName}
         |      returning ${table.columns.map(_.columnNameQuoted).mkString(",\n                ")}
         |    $tq
         |""".stripMargin
      }
      .getOrElse("")

    val updateDef = pkColumn
      .map { p =>
        s"""  override def updateSql(item: ${Names.toClassCamelCase(table.name)}): Fragment =
         |    sql$tq
         |      update ${table.nameWithSchema}
         |         set ${table.columns
             .filterNot(_.isPrimaryKey)
             .map(tc => s"${tc.columnNameQuoted} = $${item.${tc.scalaPropName}}")
             .mkString(",\n             ")}
         |       where ${p.tableColumn} = $${item.${p.scalaPropName}}
         |      returning ${table.columns.map(_.columnNameQuoted).mkString(",\n                ")}
         |    $tq
         |""".stripMargin
      }
      .getOrElse("")

    val crudDefs =
      s"""$insertDef
         |$insertAllDef
         |$getDef
         |$deleteDef
         |$updateDef
         |${finds.mkString("\n")}
         |${findOnes.mkString("\n")}
         |""".stripMargin

    val generateHasDefaults =
      table.columns
        .map { c =>
          if (c.hasDefault)
            s"_.${c.scalaPropName} == ${columnDefault(c.scalaPropType)}"
          else
            "_ => false"
        }
        .mkString(",\n    ")

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}.repository
       |
       |${repositoryImports(table)}
       |
       |import ${tablePackage(settings.rootPackage, table.schemaName)}.${Names.toClassCamelCase(table.name)}
       |import ${settings.rootPackage}.repository.DoobieRepository
       |
       |trait ${Names.toClassCamelCase(table.name)}Repository extends DoobieRepository[${Names.toClassCamelCase(table.name)}, ${pkColumn.get.scalaPropType}] {
       |  val tableColumns = List(${table.columns.map(_.columnNameQuoted).mkString(",")})
       |
       |  val hasDefault: List[${Names.toClassCamelCase(table.name)} => Boolean] = List(
       |    $generateHasDefaults
       |  )
       |
       |$crudDefs
       |}
       |
       |object ${Names.toClassCamelCase(table.name)}DefaultRepository extends ${Names.toClassCamelCase(table.name)}Repository
       |
       """.stripMargin
  }

  override def generateForView(
    view: View,
  ): List[GeneratedFile] = {
    System.out.println(s"Processing view: ${view.nameWithSchema}")
    val (repositoryPath: String, modelPath: String) = paths(view)

    val repository: String =
      generateViewRepository(settings, view)

    //noinspection DuplicatedCode
    List(
      GeneratedFile(
        modelPath,
        Names.toClassCamelCase(view.name) + ".scala",
        generateDataClass(settings, view),
      ),
      GeneratedFile(repositoryPath, Names.toClassCamelCase(view.name) + "Repository.scala", repository),
    )
  }

  private def paths(table: NamedWithSchema) = {
    val repositoryPath =
      Seq(table.schemaName, "repository").filterNot(_.isEmpty).mkString(File.separator)
    val modelPath = Seq(table.schemaName).filterNot(_.isEmpty).mkString(File.separator)
    (repositoryPath, modelPath)
  }

  private def generateDataClass(settings: Settings, table: NamedWithSchema with Columns): String = {

    def columnWithDefault(column: Column) =
      column.scalaPropWithComment + " = " + columnDefault(column.scalaPropType)

    val columnList =
      table.columns
        .map(c => if (c.hasDefault) columnWithDefault(c) else c.scalaPropWithComment)
        .mkString(",\n  ")

    s"""package ${tablePackage(settings.rootPackage, table.schemaName)}
       |
       |import io.circe.{Decoder, Encoder}
       |import io.circe.generic.extras.semiauto._
       |import io.circe.generic.extras.Configuration
       |
       |case class ${Names.toClassCamelCase(table.name)} (
       |  $columnList
       |)
       |
       |object ${Names.toClassCamelCase(table.name)} {
       |
       |  implicit val customConfig: Configuration = Configuration.default.withSnakeCaseMemberNames
       |
       |  implicit val decode${Names.toClassCamelCase(table.name)}: Decoder[${Names.toClassCamelCase(table.name)}] = deriveConfiguredDecoder
       |
       |  implicit val encode${Names.toClassCamelCase(table.name)}: Encoder[${Names.toClassCamelCase(table.name)}] = deriveConfiguredEncoder
       |
       |}
       |
       |""".stripMargin
  }

  private def columnDefault(p: String) =
    p match {
      case "Int"                       => "Int.MinValue"
      case "Long"                      => "Long.MinValue"
      case "Float"                     => "Float.NaN"
      case "Double"                    => "Double.NaN"
      case "BigDecimal"                => "BigDecimal(0)"
      case "Boolean"                   => "false"
      case "Array[Byte]"               => "Array[Byte]()"
      case "String"                    => "\"\""
      case "java.util.UUID"            => "new java.util.UUID(0,0)"
      case "java.time.LocalDate"       => "java.time.LocalDate.MIN"
      case "java.time.LocalTime"       => "java.time.LocalTime.MIN"
      case "java.time.Instant"         => "java.time.Instant.MIN"
      case "java.time.LocalDateTime"   => "java.time.LocalDateTime.MIN"
      case t if t.startsWith("Array[") => s"$t()"
      case t                           => throw new UnsupportedOperationException(s"Unsupported primary key type: $t")
    }

  private def generateViewRepository(settings: Settings, view: View): String = {

    val filters: List[(String, String, String)] = for {
      col <- view.columns
      ann <- col.annotations.filter(ColumnAnnotation.filterValues.contains)
    } yield {
      val (paramName, paramNameVal) = ann match {
        case FilterEq   => (s"${col.scalaPropName}_=", s"${col.scalaPropName}")
        case FilterLt   => (s"${col.scalaPropName}_<", s"${col.scalaPropName}")
        case FilterGt   => (s"${col.scalaPropName}_>", s"${col.scalaPropName}")
        case FilterGtEq => (s"${col.scalaPropName}_>=", s"${col.scalaPropName}")
        case FilterLtEq => (s"${col.scalaPropName}_<=", s"${col.scalaPropName}")
        case _          => throw new IllegalArgumentException("Only filter annotations allowed!")
      }

      val filterOp = ann match {
        case FilterEq   => "="
        case FilterLt   => "<"
        case FilterGt   => ">"
        case FilterGtEq => ">="
        case FilterLtEq => "<="
        case _          => throw new IllegalArgumentException("Only filter annotations allowed!")
      }

      (
        s"$paramName : Option[${col.scalaPropType}] = None,",
        s"""val ${paramNameVal}Filter = $paramName.map(v => fr"${col.name} $filterOp $$v")""",
        s"${paramNameVal}Filter",
      )
    }
    val (limitParam, limitFr, rLimitFr) =
      if (view.annotations.contains(Limit))
        (
          "      limit: Option[Int] = None,\n",
          "    val limitFr: Fragment = if (limit.isDefined) Fragment.const(s\"LIMIT $${limit.get}\") else Fragment.empty\n",
          " ++ limitFr",
        )
      else
        ("", "", "")

    val (offsetParam, offsetFr, rOffsetFr) =
      if (view.annotations.contains(Offset))
        (
          "      offset: Option[Int] = None\n",
          "    val offsetFr: Fragment = if (offset.isDefined) Fragment.const(s\"OFFSET $${offset.get}\") else Fragment.empty\n",
          " ++ offsetFr",
        )
      else
        ("", "", "")

    val (whereFr, rWhereFr) =
      if (filters.nonEmpty)
        (
          s"    val whereFr: Fragment = whereAndOpt(${filters.map(f => f._3).mkString(",")})\n",
          " ++ whereFr",
        )
      else
        ("", "")

    s"""package ${tablePackage(settings.rootPackage, view.schemaName)}.repository
       |
       |${repositoryImports(view)}
       |import Fragments.whereAndOpt
       |
       |import ${tablePackage(settings.rootPackage, view.schemaName)}.${Names.toClassCamelCase(view.name)}
       |
       |trait ${Names.toClassCamelCase(view.name)}Repository {
       |  def listFiltered(${filters
         .map(f => "      " + f._1)
         .mkString("\n", "\n", "\n")}$offsetParam$limitParam  ): Query0[${Names.toClassCamelCase(view.name)}] = {
       |${filters.map(f => "    " + f._2).mkString("\n")}
       |    val selectFr: Fragment =
       |      fr${tq}select ${view.columns
         .map(_.tableColumn)
         .mkString(",")} from ${view.nameWithSchema}$tq
       |$whereFr$offsetFr$limitFr
       |    val q: Fragment = selectFr$rWhereFr$rLimitFr$rOffsetFr
       |
       |    q.query[${Names.toClassCamelCase(view.name)}]
       |  }
       |}
       |
       |object ${Names.toClassCamelCase(view.name)}DefaultRepository extends ${Names.toClassCamelCase(view.name)}Repository
       |
       |""".stripMargin
  }

  private def tablePackage(rootPackage: String, schemaName: String): String =
    List(rootPackage, schemaName).filterNot(_.isEmpty).mkString(".")

  private def repositoryImports(t: Columns): String = {
    val columnTypes = t.columns.map(_.scalaPropType).toSet

    val sb =
      new StringBuilder()
        .append("import doobie._\n")
        .append("|import doobie.implicits._\n")

    if (columnTypes.contains("java.util.UUID")
        || columnTypes.contains("Option[java.util.UUID]")) {
      sb.append("|import doobie.postgres.implicits._\n")
    }

    if (columnTypes.contains("java.time.Instant")
        || columnTypes.contains("Option[java.time.Instant]")
        || columnTypes.contains("java.time.LocalDate")
        || columnTypes.contains("Option[java.time.LocalDate]")
        || columnTypes.contains("java.time.LocalTime")
        || columnTypes.contains("Option[java.time.LocalTime]")) {
      sb.append("|import doobie.implicits.javatime._\n")
    }

    if (columnTypes.contains("io.circe.Json") || columnTypes.contains("Option[io.circe.Json]")) {
      sb.append("|import doobie.postgres.circe.jsonb.implicits._\n")
    }

    sb.mkString
  }
}
