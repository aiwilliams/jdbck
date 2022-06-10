# JDBCK

```postgresql
create table entities
(
    id              uuid      default gen_random_uuid() not null,
    textColumn      text                                not null,
    textArrayColumn text[]                              not null,
    jsonbColumn     jsonb                               not null,
    created_at      timestamp default CURRENT_TIMESTAMP not null,
    updated_at      timestamp default CURRENT_TIMESTAMP not null
);

INSERT INTO entities (textColumn, textArrayColumn, jsonbColumn)
VALUES ('Text in the column',
        '{"first", "second", "third"}',
        '{
          "longValue": 123
        }');
```