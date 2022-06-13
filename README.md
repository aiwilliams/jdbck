# JDBCK

A project for exploring Kotlin Builders, Coroutines, and JDBC connectivity.

- https://jdbc.postgresql.org/documentation/head/index.html
- https://docs.oracle.com/javase/tutorial/jdbc/basics/index.html
- [kotlin-coroutines-jdbc](https://github.com/michaelbull/kotlin-coroutines-jdbc)

Additional resources, each building on the other:

- https://proandroiddev.com/what-is-concurrent-access-to-mutable-state-f386e5cb8292
- https://elizarov.medium.com/blocking-threads-suspending-coroutines-d33e11bf4761
- https://elizarov.medium.com/explicit-concurrency-67a8e8fd9b25
- https://elizarov.medium.com/structured-concurrency-722d765aa952
- https://elizarov.medium.com/coroutine-context-and-scope-c8b255d59055

Performance considerations:
- https://suchakjani.medium.com/non-blocking-jdbc-early-2021-update-b8b2a24a3b83#35d5
- https://technology.amis.nl/software-development/performance-and-tuning/spring-blocking-vs-non-blocking-r2dbc-vs-jdbc-and-webflux-vs-web-mvc/


```postgresql
create table entities
(
    contextId       uuid                                not null, --  Provides a way to scope data for each test/execution
    id              uuid      default gen_random_uuid() not null unique,
    textColumn      text                                not null,
    textArrayColumn text[]                              not null,
    jsonbColumn     jsonb                               not null,
    createdAt      timestamp default CURRENT_TIMESTAMP not null,
    updatedAt      timestamp default CURRENT_TIMESTAMP not null
);

ALTER TABLE entities OWNER TO test;

INSERT INTO entities (contextId, id, textColumn, textArrayColumn, jsonbColumn)
VALUES (
    'uuid-here',
    'uuid-here',
    'Text in the column',
    '{"first", "second", "third"}',
    '{
      "longValue": 123
    }'
);
```


[Vert.x PostgreSQL Java client](https://vertx.io/docs/vertx-pg-client/java/)
- A custom (not a JDBC driver) reactive, non-blocking, database client with a callback-oriented design (no particular integration with Kotlin coroutines).
- Provides a custom connection pool implementation.
- Vert.x query functions and their callbacks in thread blocking functions (runBlocking).
- Prepared statement variables take the form "$1", with values provided as a custom Tuple type.

JDBC PosgreSQL driver
- Blocking API
- Oracle was working on non-blocking extensions to JDBC but abandoned the project with expectations that [Loom](https://openjdk.java.net/projects/loom/) would bring Fibers to the JVM and allow existing JDBC drivers to function in a non-blocking fashion.
- Prepared statement variables take the form "?", with values provided as a List<Any> equal in length to the number of variable references.

[R2DBC PostgreSQL](https://github.com/pgjdbc/r2dbc-postgresql) driver for the reactive [R2DBC SPI](https://github.com/r2dbc/r2dbc-spi)
- A custom (not a JDBC driver) non-blocking alternative to JDBC.
- Prepared statement variables take the form "$1" (matching PostgreSQL’s index parameters), with values provided using statement.bind("$1", any).
