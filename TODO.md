# TODO

## Preamble

We will base all examples on this schema: 

```postgresql
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

**Example:**

We have a table `auth.users` with a complex user definition and we want a simpler view for 
display in a list of users. We create a view `users_view` for that. 

```postgresql
CREATE OR REPLACE VIEW auth.users_view as (
    SELECT id, name, email, active, groups from auth.users
);

COMMENT ON COLUMN auth.users_view.name IS
'Users real name. 
@FilterEq';

COMMENT ON COLUMN auth.users_view.active IS 
'@FilterEq';

COMMENT ON COLUMN auth.user_view.created IS
'@FilterGtEq';
```

yields this code:

```scala
package your.custom_package.auth

case class UserView (
  id: java.util.UUID,
  name: String,
  email: String,
  active: Boolean,
  groups: String
)
```
```scala
package your.custom_package.auth.doobie

import doobie._
import doobie.implicits._
import Fragments.{ in, whereAndOpt }

object UserViewDoobieRepository {
  def listFiltered(
      name: Option[String], 
      active: Option[Boolean], 
      created: Option[java.time.Instant],
      offset: Option[Int] = None,
      limit: Option[Int] = None
): Query0[UserView]] = {
    val nameFilter = name.map(v => fr"name=$v") 
    val activeFilter = active.map(v => fr"active=$v") 
    val createdFilter = created.map(v => fr"crated >= $v")
    val q: Fragment = 
      fr"""SELECT "id", "name", "email", "active", "groups" FROM "auth"."user_view" """ ++
      whereAndOpt(nameFilter, activeFilter, createdFilter)                              ++
      if (offset.isDefined) fr"OFFSET ${offset.get}" else Fragment.empty                ++
      if (limit.isDefined) fr"LIMIT ${limit.get}" else Fragment.empty
  
    q.query[UserView]
  }
}
```


### Belongs-to relationships

**Example:**

A `Book` belongs to an `Author`. A foreign key on the `Book` that points to `Author` can be annotated with 
`@BelongsTo(owner_table)`. We should generate CRUD code for both entities and add a method on `Book` repository
named `forAuthor[K](id: K): Seq[Book]`.

### Master-detail relationships

This type of relationship has one row in the master table and in different table(s) one or more detail rows. A foreign 
key that points to the master table can be annotated in its description with `@Detail(master_table_name)` annotation.
For this type of hierarchy we can skip creating CRUD code for detail classes and bundle persistence
in the repository for the master class.

- investigate using JSON as encoding for detail persistence and fetching. That way we could save use a single round-trip
  for all data in this master-detail hierarchy.

**Example:**

An `Invoice` has one or more `InvoiceItem`s. Every `InvoiceItem` can have one or more `ItemTax` entries. 
Both `InvoiceItem` and `ItemTax` have `@Detail()` annotation on foreign keys to parent table. 

### Enums

Do Postgres enums have any value compared to lookup tables? This makes it a question of having enum values present at
compile time or loaded from the database.

### Lookup tables

Every application will have one or more lookup tables (not for enums but enums would fall under this category). Data 
that doesn't change often but is needed often. These tables can be annotated with the `@Lookup` annotation. Apart from 
the CRUD operations on these tables we can then generate code that loads data for all these tables at once. This can be
invoked on every transaction and can be improved with versioning so that the data is loaded on app start and then served
from cache unless some of it is updated.

