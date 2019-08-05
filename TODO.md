# TODO

## Preamble

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

### Views

Any query with complex joins that is known in advance should be defined as a view in the database. The view definition 
should define a column projection and necessary joins with a minimal set of `WHERE` clauses. We can add column 
descriptions that contain annotations:

- `@FilterEq`
- `@FilterGt`
- `@FilterLt`
- `@FilterGtEq`
- `@FilterLtEq`
- `@FilterBetween`

The annotated columns are put in the argument list for the filter over the view.

View-level annotations `@Limit` and `@Offset` will allow to specify the limit and offset to the query result. Be careful
with using `@Limit` and `@Offset` for pagination. There are serious performance implications when your results have more
than a couple of thousand records in total.

**Example:**

We have a table `auth.users` with a complex user definition and we want a simpler view for 
display in a list of users. We create a view `users_list_view` for that. 

```sql
CREATE OR REPLACE VIEW auth.user_list_view as (
    SELECT id, name, email, active, groups, created from auth.users
);

COMMENT ON VIEW auth.user_list_view IS
'@Limit
 @Offset';

COMMENT ON COLUMN auth.user_list_view.name IS
'Users real name. 
@FilterEq';

COMMENT ON COLUMN auth.user_list_view.active IS 
'@FilterEq';

COMMENT ON COLUMN auth.user_list_view.created IS
'@FilterGtEq';
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
      name: Option[String], 
      active: Option[Boolean], 
      created: Option[java.time.Instant],
      offset: Option[Int] = None,
      limit: Option[Int] = None,
): Query0[UserListView]] = {
    val nameFilter = name.map(v => fr"name=$v") 
    val activeFilter = active.map(v => fr"active=$v") 
    val createdFilter = created.map(v => fr"crated >= $v")
    val q: Fragment = 
      fr"""SELECT "id", "name", "email", "active", "groups" FROM "auth"."user_view" """ ++
      whereAndOpt(nameFilter, activeFilter, createdFilter)                              ++
      if (offset.isDefined) fr"OFFSET ${offset.get}" else Fragment.empty                ++
      if (limit.isDefined) fr"LIMIT ${limit.get}" else Fragment.empty
  
    q.query[UserListView]
  }
}
```


### Belongs-to relationships

**Example:**

A `Book` belongs to an `Author`. A foreign key on the `Book` that points to `Author` can be annotated with 
`@BelongsTo`. CRUD code will be generated for both entities and a method will be added on `Book` repository
named `forAuthor[K](id: K): Query0[Book]`. 

### Master-detail relationships

This type of relationship has one row in the master table and one or more detail rows in one or more other table(s). A 
foreign key that points to the master table can be annotated in its description with `@Detail` annotation.
For this type of hierarchy CRUD code for detail classes will not be generated. Persistence operations will be provided 
by the repository for the master class.

- investigate using JSON as encoding for detail persistence and fetching. That way we save use a single round-trip
  for all data in this master-detail hierarchy.

**Example:**

An `Invoice` has one or more `InvoiceItem`s. Every `InvoiceItem` can have one or more `ItemTax` entries. 
Both `InvoiceItem` and `ItemTax` have `@Detail` annotation on foreign keys to parent table. 

### Enums

Do Postgres enums have any value compared to lookup tables? This makes it a question of having enum values present at
compile time or loaded from the database.

### Lookup tables

Every application will have one or more lookup tables (not for enums but enums could fall under this category). Data 
that doesn't change often but is needed often. These tables can be annotated with the `@Lookup` annotation. Apart from 
the CRUD operations on these tables we can then generate code that loads data for all these tables at once. This can be
invoked on every transaction and can be improved with versioning so that the data is loaded on app start and then served
from cache unless some of it is updated.

### Commands

All changes to domain models should come in via commands. A column annontation `@Command(CommandName)` can be put on 
a column that participates in this particular command. 

**Example**

A `CreateUser` command can be defined by annotating columns on `auth.users` table:

```sql
COMMENT ON COLUMN auth.users.name IS 
'@Command(CreateUser)';

COMMENT ON COLUMN auth.users.email IS 
'@Command(CreateUser)';

COMMENT ON COLUMN auth.users.groups IS
'@Command(CreateUser)';
``` 

Which will generate:

```scala
package your.custom_package.auth.commands
 
case class CreateUser (
  name: String,
  email: String,
  groups: String,
)
```

### Events

Events are just normal tables that have a table annotation `@Event`. Events represent an immutable log of changes that 
were already done so the generated repository will not have any update or delete methods - only create and query methods.

We will not impose any structure on the event tables but some incrementing id and / or timestamp would be useful to have
there.

### Entity versioning

History tracking for entities can be enabled by adding a `@Versioning` annotation on the column that will be used to
keep the entity version. The generated repository will not have `UPDATE` statements, only insert. `SQL` generated for 
queries will always select the latest version using the provided versioning column.

### Optimistic concurrency (Version checking)
On a table that has a `@Versioning` column you can add a `@VersionCheck` annotation on the table-level comment. The
generated repository will have code that checks on update (remember, it will actually be an insert) if the latest version
in the table is the one that was used to produce that new entity version. The return value for this operation will contain
`Either` the `VersionCheckFailed` error or the success value.
 - **TODO**: work on this some more.
 
