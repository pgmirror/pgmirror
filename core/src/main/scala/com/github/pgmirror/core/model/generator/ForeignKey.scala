package com.github.pgmirror.core.model.generator

case class ForeignKey(
  table: Table,
  column: Column,
  foreignTable: Table,
  foreignColumn: Column,
)
