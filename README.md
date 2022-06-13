# JDBCK

- https://jdbc.postgresql.org/documentation/head/index.html
- https://docs.oracle.com/javase/tutorial/jdbc/basics/index.html 

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