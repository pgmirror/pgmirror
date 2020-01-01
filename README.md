# About

Pgmirror is a code generator written in Scala that will take your Postgresql database schema and generate 
some code for you:

- Generate model classes for your domain models.
- Generate the repositories using the Doobie library.
- What ever else you decide to add yourself. It is easy to extend.

# Usage

Currently Pgmirror is just a library meant to be used as an "engine" for various build tool tasks. Initial goal is to
provide an sbt plugin.  

Until we make an sbt plugin you can add the below task directly in your `build.sbt`. Check out Sbt documentation on how
to integrate it with your build steps. 

The generated code depends on `doobie-core`, `doobie-postgres`, `doobie-postgres-circe`, `circe-core`, `circe-generic` 
and `circe-generic-extras`. There are no hard requirements on Doobie or Circe versions but do use the latest you can
afford.

* Sample `project/build.sbt`
```scala
resolvers in ThisBuild += "pgmirror" at "https://maven.pkg.github.com/pgmirror/pgmirror"

libraryDependencies += "com.github.pgmirror" %% "pgmirror-doobie" % "0.1.0"
```

* Sample `build.sbt`
```scala
val pgMirror = taskKey[Unit]("Mirrors the Postgres database into code")

lazy val root = (project in file("."))
  .settings(
    name := "Pgmirror test",
    libraryDependencies ++= Seq(
      "org.tpolecat"             %% "doobie-core" % "0.8.6",
      "org.tpolecat"             %% "doobie-postgres" % "0.8.6",
      "org.tpolecat"             %% "doobie-postgres-circe" % "0.8.6",
      "io.circe"                 %% "circe-core" % "0.12.3",
      "io.circe"                 %% "circe-generic" % "0.12.3",
      "io.circe"                 %% "circe-generic-extras" % "0.12.2",
    ),
    pgMirror := {
      import com.github.pgmirror.doobie.DoobieGenerator
      import com.github.pgmirror.core.Settings

      val settings = Settings(
        url = "jdbc:postgresql://localhost:5432/your_db",
        user = "your_db_username",
        password = "your_db_password",
        rootPackage = "com.project.fabulous.your.data",
        rootPath = ((Compile / unmanagedSourceDirectories).value).head.getAbsolutePath
      )

      val gen = new DoobieGenerator(settings)
      gen.generate()
    }
  )
```

then start `sbt` shell and run `pgMirror`. 

# Why? How? What?

Pgmirror is an exploration in creating a tool with maximum impact that is as simple as possible. The whole codebase 
currently sits at 850 lines. It should be understandable to anyone who invests an hour of their time. Current implementation
does not use many Postgres-specific features but it will in the future, hence the focus on Postgresql. 

The aim is to blend in with your existing development process. That means you get to continue to use your favourite schema
evolution approach. The only hard requirement is when you generate code you need access to a running Postgresql instance with
your latest schema.

Pgmirror generates Doobie code needed to CRUD your way through typical business database schemas. People accept writing
those as normal but that is just silly. To make pgmirror a little bit more useful than just a CRUD generator we are
experimenting with adding annotations to tables and columns in the form of SQL COMMENTs. You would do something 
like this:

```sql
COMMENT ON COLUMN auth."users_list_view".name IS $$
Users real name.

@NotNull
@FilterEQ
$$;
```

All comments on tables and columns are copied as comments in Scala code. 

To see which annotations are currently supported just search for the `@` sign in this document. Contributions welcome
to create a proper reference document.

`COMMENT`s in Postgresql are not very ergonomic but some GUI database management tools (all are commercial so I won't
name names) make this less of a pain.



## Philosophy

To be truly effective you also need to embrace certain patterns in system design. We will pluck ideas from the DDD
community, CQRS and what ever else when it will contribute to our goal of simple and maintainable project development. 

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

Postgresql reports all view columns as nullable which is unfortunate because the generated model class will have all 
column types set to `Option[Whatever]`. If you wish to override nullability of a certain column use the `@NotNull`
annotation on that column. This annotation is also available on table columns. Be careful when you use it. You WILL get 
random exceptions when your expectations do not meet reality.

#### Primary keys

The only supported table structure is one that has a single primary key. It is also expected that this primary key is
generated by the database but you can override this by putting a `@AppPk` annotation on the primary key column.


### Examples

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
