# TODO

## About

Pgmirror is a code generator that will take your database schema and generate some code for you:

- Generate model classes for your domain models.
- Generate the repositories using the Doobie library.
- Generate the Http4s endpoints for CRUD operations.
- What ever else you decide to add yourself. It is easy to extend.
 
However, to be truly effective you also need to embrace certain patterns in system design. This project's aim is to be
as simple as possible, both in the code and in the cognitive load on the programmer. We will pluck ideas from the DDD
community, CQRS and what ever else when it will contribute to our goal of simple and maintainable project development. 

### Philosophy

The generated code should account for majority if not all uses of your presistence layer. There should be no custom SQL
code in your main source. This is not 100% possible but if you organize your code carefully then using the repository 
pattern should account for almost all of your persistence needs.
Use tables for storage (kind of obvious, isn't it!) but all views into the data should be done through SQL views. This
typically means adopting a kind of CQRS approach where data is changed (created, updated, deleted) via commands whose
handlers use model repositories to do the modifications. Any kind of view into the data is done through database views. 
The repository code generated for views only allows read and filtered list operations. You can create simple views to
support entity lists or per-entity edit views. Reports should also be done via views whenever possible. Sometimes we
want to run periodic jobs that prepare reporting data. This is the only place where it's expected to have custom SQL
code in your application. 

## Implementation

We will base all examples on this schema: 

```sql
CREATE TABLE auth.users (
    id uuid not null primary key,
    name text not null,
    email text not null,
    active boolean not null default false,
    groups text not null,
    created timestamptz not null default now(),
    last_update timestamptz
);
```

### Tables and Views

Any query with complex joins that is known in advance should be defined as a view in the database. The view definition 
should define a column projection and necessary joins with a minimal set of `WHERE` clauses. 

For views we can add column descriptions that contain annotations:

- `@FilterEQ`
- `@FilterGT`
- `@FilterLT`
- `@FilterGE`
- `@FilterLE`

The annotated columns are put in the argument list for the filter over the view.

View level annotations `@Limit` and `@Offset` will allow to specify the limit and offset to the query result. Be careful
with using `@Limit` and `@Offset` for pagination. There are serious performance implications when your results have more
than a couple of thousand records in total.

View definitions create new model classes. This means that a view that returns columns identical to a table it selects
from will yield a different class than the original table. 

There are two additional annotations: `@Find` and `@FindOne`. You apply these annotations to table columns only. For 
every column with these annotations you will get a method `findBy` + `ColumnName` in the table repository. `@Find` returns 
a list and `@FindOne` returns an option of the model class and throws if there is more than one result.

Make sure the columns you filter on are properly indexed!

Postgresql reports all view columns as nullable which is inconvenient because generated model class will have all column
types set to `Option[Whatever]`. If you wish to override nullability of a certain column use the `@NotNull` annotation
on that column. This annotation is also available on table columns. Be careful when you use it.

**Example:**

We have a table `auth.users` with a complex user definition and we want a simpler view for 
display in a list of users. We create a view `users_list_view` for that. 

```sql
CREATE OR REPLACE VIEW auth."users_list_view" as (
    SELECT id, name, email, active, groups, created from auth.users
);

COMMENT ON VIEW auth."users_list_view" IS $$
@Limit 
@Offset
$$;

COMMENT ON COLUMN auth."users_list_view".name IS $$
Users real name.

@FilterEQ
$$;

COMMENT ON COLUMN auth."users_list_view".active IS $$
@FilterEQ
$$;

COMMENT ON COLUMN auth."users_list_view".created IS $$
@FilterGE
$$;
```

yields this code:

```scala
package your.custom_package.auth

case class UserListView (
  id: java.util.UUID,
  name: String,
  email: String,
  active: Boolean,
  groups: String,
  created: java.time.Instant
)
```
```scala
package your.custom_package.auth.doobie

import doobie._
import doobie.implicits._
import Fragments.{ in, whereAndOpt }

object UserListViewDoobieRepository {
  def listFiltered(
      name: Option[String] = None, 
      active: Option[Boolean] = None, 
      created: Option[java.time.Instant] = None,
      offset: Option[Int] = None,
      limit: Option[Int] = None,
): Query0[UserListView]] = {
    val nameFilter = name.map(v => fr"name = $v") 
    val activeFilter = active.map(v => fr"active = $v") 
    val createdFilter = created.map(v => fr"crated >= $v")
    val q: Fragment = 
      fr"""SELECT "id", "name", "email", "active", "groups" FROM "auth"."users_list_view" """ ++
      whereAndOpt(nameFilter, activeFilter, createdFilter)                              ++
      if (offset.isDefined) Fragment.const(s"OFFSET ${offset.get}") else Fragment.empty ++
      if (limit.isDefined) Fragment.const(s"LIMIT ${limit.get}") else Fragment.empty
  
    q.query[UserListView]
  }
}
```

### Master-detail relationships

This type of relationship has one row in the master table and zero or more detail rows in one or more other table(s). A 
foreign key that points to the master table can be annotated in its description with `@Detail` annotation.
For this type of hierarchy CRUD code for detail classes will not be generated. Persistence operations will be provided 
by the repository for the master class.

We are using a somewhat unusual approach with JSON as encoding for detail persistence and fetching. That way we can 
use a single round-trip for all data in this master-detail hierarchy.
  

**Example:**

An `Invoice` has one or more `InvoiceItem`s. Every `InvoiceItem` can have one or more `ItemTax` entries. 
Both `InvoiceItem` and `ItemTax` have `@Detail` annotation on foreign keys to parent table. 

Generated query for fetching a hierarchy in a `invoice -> item -> item_tax` relationship via JSON:
 
```sql
  SELECT row_to_json(master)
  FROM (
           SELECT invoice.*,
                  CASE WHEN count(items_detail) = 0 
                       THEN ARRAY []::JSON[] 
                       ELSE array_agg(items_detail.item) 
                  END AS items,
                  CASE WHEN count(invoice_compensation_detail) = 0 
                       THEN ARRAY []::JSON[]
                       ELSE array_agg(invoice_compensation_detail.compensation) 
                  END AS compensations,
                  CASE WHEN count(invoice_tax_detail) = 0 
                       THEN ARRAY []::JSON[]
                       ELSE array_agg(invoice_tax_detail.tax) 
                  END AS invoice_taxes
           FROM invoice
                LEFT OUTER JOIN (
                    SELECT items_detail_inner.invoice_id, row_to_json(items_detail_inner) item
                     FROM (SELECT invoice_item.*,
                                  CASE WHEN count(item_tax_detail) = 0 
                                       THEN ARRAY []::JSON[]
                                       ELSE array_agg(item_tax_detail.tax) 
                                  END AS taxes
                         FROM invoice_item
                              LEFT OUTER JOIN (
                                  SELECT item_tax.item_id, row_to_json(item_tax) tax
                                    FROM item_tax
                              ) item_tax_detail ON invoice_item.id = item_tax_detail.item_id
                         GROUP BY invoice_item.id
                        ) items_detail_inner
                ) items_detail ON items_detail.invoice_id = invoice.id
                LEFT OUTER JOIN (
                    SELECT invoice_compensation.invoice_id, row_to_json(invoice_compensation) compensation
                     FROM invoice_compensation
                ) invoice_compensation_detail ON invoice.id = invoice_compensation_detail.invoice_id
                LEFT OUTER JOIN (
                    SELECT invoice_tax.invoice_id, row_to_json(invoice_tax) tax
                      FROM invoice_tax
                ) invoice_tax_detail ON invoice_tax_detail.invoice_id = invoice.id
           GROUP BY invoice.id
       ) master
  WHERE master.id = 37;
```

Persisting such entities is done in layers: first the top-level entities are persisted, then one level below, etc. This 
is so that we get autogenerated keys that the lower-level entities need to refer to.

### Enums

Do Postgres enums have any value compared to lookup tables? This makes it a question of having enum values present at
compile time or loaded from the database.

### Lookup tables

Every application will have one or more lookup tables (not for enums but enums could fall under this category). Data 
that doesn't change often but is needed often. These tables can be annotated with the `@Lookup` annotation. Apart from 
the CRUD operations on these tables we can then generate code that loads data for all these tables at once. This can be
invoked on every transaction and can be improved with versioning so that the data is loaded on app start and then served
from cache unless some of it is updated.

### Events

Events are just normal tables that have a table annotation `@Event`. Events represent an immutable log of changes that 
were already done so the generated repository will not have any update or delete methods - only create and query methods.

We will not impose any structure on the event tables but some incrementing id and / or timestamp would be useful to have
there.

### Entity versioning

You can track entity versions by adding a `@Versioning` annotation on the column that will be used to
keep the entity version. Supported column type is `timestamp(tz)`. You can set the value for this column
any way you like, the generated repository will always ignore the set value and use `now()` for all `INSERT` and 
`UPDATE` operations.
 
### Optimistic concurrency (Version checking)
On a table that has a `@Versioning` column you can add a `@VersionCheck` annotation on the table-level comment. The
generated repository will have code that checks on update if the latest version in the table is the one that was used to
produce that new entity version. The return value for this operation will contain `Either` the `VersionCheckFailed` 
error or the success value.
 - **TODO**: work on this some more.
 
### Entity history

When a table has `@Versioning` and `@VersionCheck` enabled you can also add `@History` annotation on the table-level
comment. This will turn all `UPDATE` queries into `INSERT` and all queries will be generated so that they always select
the latest version of the entity. The table in question should have a composite primary key (identity+version).

