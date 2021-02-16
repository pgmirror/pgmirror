package com.github.pgmirror.core.model.generator

import com.github.pgmirror.core.model.database
import scala.util.matching.Regex

sealed abstract class Annotation(val regex: Regex)

sealed abstract class ColumnAnnotation(override val regex: Regex) extends Annotation(regex)

object ColumnAnnotation {
  case object Detail     extends ColumnAnnotation("""@Detail\b""".r)
  case object FilterEq   extends ColumnAnnotation("""@FilterEQ\b""".r)
  case object FilterGt   extends ColumnAnnotation("""@FilterGT\b""".r)
  case object FilterLt   extends ColumnAnnotation("""@FilterLT\b""".r)
  case object FilterGtEq extends ColumnAnnotation("""@FilterGE\b""".r)
  case object FilterLtEq extends ColumnAnnotation("""@FilterLE\b""".r)
  case object Find       extends ColumnAnnotation("""@Find\b""".r)
  case object FindOne    extends ColumnAnnotation("""@FindOne\b""".r)
  case object NotNull    extends ColumnAnnotation("""@NotNull\b""".r)
  case object Versioning extends ColumnAnnotation("""@Versioning\b""".r)

  val values: List[ColumnAnnotation] = List[ColumnAnnotation](
    Detail,
    FilterEq,
    FilterGt,
    FilterLt,
    FilterGtEq,
    FilterLtEq,
    Find,
    FindOne,
    NotNull,
    Versioning,
  )

  val filterValues: Set[ColumnAnnotation] = Set(
    FilterEq,
    FilterGt,
    FilterLt,
    FilterGtEq,
    FilterLtEq,
  )

  def findAllForDbColumn(in: database.Column): List[ColumnAnnotation] =
    values
      .filter(_.regex.findAllIn(in.description.getOrElse("")).nonEmpty)
}

sealed abstract class TableAnnotation(override val regex: Regex) extends Annotation(regex)

object TableAnnotation {
  case object Limit        extends TableAnnotation("""@Limit\b""".r)
  case object Offset       extends TableAnnotation("""@Offset\b""".r)
  case object Lookup       extends TableAnnotation("""@Lookup\b""".r)
  case object Event        extends TableAnnotation("""@Event\b""".r)
  case object VersionCheck extends TableAnnotation("""@VersionCheck\b""".r)
  case object History      extends TableAnnotation("""@History\b""".r)

  val values: List[TableAnnotation] = List[TableAnnotation](
    Limit,
    Offset,
    Lookup,
    Event,
    VersionCheck,
    History,
  )

  def findAllForDbTable(in: database.Table): List[TableAnnotation] =
    values
      .filter(_.regex.findAllIn(in.description.getOrElse("")).nonEmpty)
}

