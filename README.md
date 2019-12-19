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

COMMENT ON COLUMN auth."users_list_view".id IS $$
@NotNull
$$;

COMMENT ON COLUMN auth."users_list_view".name IS $$
Users real name.

@NotNull
@FilterEQ
$$;

COMMENT ON COLUMN auth."users_list_view".active IS $$
@NotNull
@FilterEQ
$$;

COMMENT ON COLUMN auth."users_list_view".created IS $$
@NotNull
@FilterGE
$$;
```

yields this code:

```scala
package your.custom_package.auth

case class UserListView (
  id: java.util.UUID,
  name: String,
  email: Option[String],
  active: Boolean,
  groups: Option[String],
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
      name_=: Option[String] = None, 
      active_=: Option[Boolean] = None, 
      created_>=: Option[java.time.Instant] = None,
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
