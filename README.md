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

```sql
SELECT pg_typeof('["hello", "world"]'::jsonb);
--> jsonb
SELECT pg_typeof('["hello", "world"]'::jsonb::text);
--> text

-- Note the string constructed for the rhs must equal the output of postgres
SELECT lower(('{"hello": ["Hello","WoRld"]}'::jsonb -> 'hello')::text) = '["hello", "world"]';
--> true
SELECT lower(('{"hello": ["Hello","WoRld"]}'::jsonb -> 'hello')::text) = '["hello","world"]';
--> false

-- Whenever there is a lower transform, cast the rhs to jsonb::text
SELECT lower(('{"hello": ["Hello","WoRld"]}'::jsonb -> 'hello')::text) = '["hello", "world"]'::jsonb::text;
--> true
SELECT lower(('{"hello": ["Hello","WoRld",true,1]}'::jsonb -> 'hello')::text) = '["hello", "world",   true, 1]'::jsonb::text;
--> true

-- We can lowercase the text form of the JSON, then compare the jsonb. This works better for Vert.x because it will attempt to coerce the right side into text in Java and fail with:
-- Parameter at position[1] with class = [io.vertx.core.json.JsonArray] and value = [["world"]] can not be coerced to the expected class = [java.lang.String] for encoding.
SELECT lower(('{"hello": ["Hello","WoRld",true,1]}'::jsonb -> 'hello')::text)::jsonb = '["hello", "world",   true, 1]'::jsonb;
--> true

-- Proof that supporting UPPERCASE is not possible with the approach of running the search in SQL
SELECT upper(('{"hello": ["Hello","WoRld",true,1]}'::jsonb -> 'hello')::text);
--> '["HELLO", "WORLD", TRUE, 1]' (text)
SELECT upper(('{"hello": ["Hello","WoRld",true,1]}'::jsonb -> 'hello')::text)::jsonb;
--> [22P02] ERROR: invalid input syntax for type json Detail: Token "TRUE" is invalid. Where: JSON data, line 1: ["HELLO", "WORLD", TRUE...
SELECT upper(('{"hello": ["Hello","WoRld",true,1]}'::jsonb -> 'hello')::text) = '["HELLO", "WORLD",   true, 1]'::jsonb::text;
--> false

SELECT ('{"hello": true}'::jsonb -> 'hello') = true;
--> [42883] ERROR: operator does not exist: jsonb = boolean Hint: No operator matches the given name and argument types. You might need to add explicit type casts. Position: 46

-- Compare to unknown, it will be coerced to jsonb
SELECT ('{"hello": true}'::jsonb -> 'hello') = 'true';
--> true
SELECT ('{"hello": true}'::jsonb -> 'hello') = 'true'::jsonb;
--> true
SELECT ('{"hello": "true"}'::jsonb -> 'hello') = 'true'::jsonb;
--> false
SELECT ('{"hello": "true"}'::jsonb -> 'hello') = 'true';
--> false
SELECT ('{"hello": "true"}'::jsonb -> 'hello') = '"true"';
--> true

SELECT ('{"hello": null}'::jsonb -> 'hello') = 'null';
--> true
SELECT ('{}'::jsonb -> 'hello') = 'null';
--> NULL
SELECT ('{}'::jsonb -> 'hello') IS NULL;
--> true
SELECT ('{}'::jsonb -> 'hello') = 'undefined';
--> [22P02] ERROR: invalid input syntax for type json Detail: Token "undefined" is invalid. Position: 35 Where: JSON data, line 1: undefined
SELECT pg_typeof(('{}'::jsonb -> 'hello'));
--> jsonb
SELECT ('{}'::jsonb -> 'hello');
--> NULL

SELECT ('{world}'::text[]);
--> {world}
```
    
* Be sure to understand the driver's treatment of Java data types in prepared statement params.
* Monitor the PostgreSQL query logs and notice the way it renders the params. This will reveal how the driver is setting what it sends for the prepared statement.
