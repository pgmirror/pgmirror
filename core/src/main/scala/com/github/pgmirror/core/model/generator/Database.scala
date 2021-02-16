package com.github.pgmirror.core.model.generator

case class Database(
  tables: List[Table],
  views: List[View],
  udts: List[Udt],
  foreignKeys: List[ForeignKey],
)
