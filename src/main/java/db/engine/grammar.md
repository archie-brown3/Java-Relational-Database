# BNF Grammar — Java Relational Database

## Top-level command dispatch

```
<command>      ::=  <use> | <create> | <drop> | <alter> | <insert> | <select> | <update> | <delete> | <join> | <left-join>
```

All commands must end with a semicolon (`;`).

## USE

```
<use>          ::=  "USE" <name>
```

## CREATE

```
<create>       ::=  <create-database> | <create-table>
<create-database> ::= "CREATE" "DATABASE" <name>
<create-table> ::=  "CREATE" "TABLE" <name> [ "(" <name-list> ")" ]
```

## DROP

```
<drop>         ::=  <drop-database> | <drop-table>
<drop-database> ::= "DROP" "DATABASE" <name>
<drop-table>  ::=  "DROP" "TABLE" <name>
```

## ALTER

```
<alter>        ::=  "ALTER" "TABLE" <name> <alteration-type> <name>
<alteration-type> ::= "ADD" | "DROP"
```

DROP on the `id` column is rejected.

## INSERT

```
<insert>       ::=  "INSERT" "INTO" <name> "VALUES" [ " " ] "(" <value-list> ")"
```

## SELECT

```
<select>       ::=  "SELECT" [ "DISTINCT" ] <attributes> "FROM" <name>
                   ["WHERE" <condition>]
                   ["GROUP BY" <name-list>]
                   ["ORDER BY" <order-list>]
                   ["LIMIT" <number> ["OFFSET" <number>]]
<attributes>   ::=  "*" | <attribute-list>
<attribute-list> ::= <aggregate> | <name> | <name> "," <attribute-list> | <aggregate> "," <name> | <name> "," <aggregate>
<aggregate>    ::=  "COUNT(*)" | "SUM(" <name> ")" | "AVG(" <name> ")"
<order-list>   ::=  <order-item> | <order-item> "," <order-list>
<order-item>   ::=  <name> [ "ASC" | "DESC" ]
```

- `ORDER BY` defaults to ASC when no direction is specified.
- Numeric columns are compared numerically; text columns lexicographically.
- `GROUP BY` requires an aggregate function in the SELECT clause.
- Multi-column `GROUP BY` and `ORDER BY` are supported.
- `LIMIT n OFFSET m` skips `m` rows then returns at most `n` rows.
- `DISTINCT` deduplicates rows by all projected columns (including `id`).

## UPDATE

```
<update>       ::=  "UPDATE" <name> "SET" <name-value-list> "WHERE" <condition>
<name-value-list> ::= <name-value> | <name-value> "," <name-value-list>
<name-value>   ::=  <name> "=" <value>
```

`id` column cannot be updated.

## DELETE

```
<delete>       ::=  "DELETE" "FROM" <name> "WHERE" <condition>
```

WHERE clause is mandatory (no unconditional deletes).

## JOIN

```
<join>         ::=  "JOIN" <name> "AND" <name> "ON" <name> "AND" <name>
<left-join>    ::=  "LEFT" "JOIN" <name> "AND" <name> "ON" <name> "AND" <name>
```

`JOIN` performs an inner join — only rows with matching values on both sides appear. `LEFT JOIN` includes all rows from the left table; unmatched right-table values are empty. Output columns are prefixed with their source table name (e.g., `users.name`, `orders.product`).

## Conditions

```
<condition>    ::=  <comparison> | <null-test> | "(" <condition> ")" | <condition> "AND" <condition> | <condition> "OR" <condition>
<comparison>   ::=  <name> <operator> <value>
<operator>     ::=  "==" | "!=" | ">" | "<" | ">=" | "<=" | "LIKE"
<null-test>    ::=  <name> "IS NULL" | <name> "IS NOT NULL"
```

- `LIKE` supports `%` as a wildcard (converted to `.*` internally).
- Numeric values are compared numerically when both sides parse as doubles.
- String values are compared lexicographically.
- Quoted values (single or double) have their quotes stripped before comparison.
- `NULL` can be inserted as a value; it is represented internally as a sentinel and serialized as an empty string in .tab files for backward compatibility.
- `NULL` never matches any equality comparison (`==` or `!=`).
- `IS NULL` and `IS NOT NULL` test for the presence of NULL.

## Lexical rules

```
<name-list>    ::=  <name> | <name> "," <name-list>
<value-list>   ::=  <value> | <value> "," <value-list>
<name>         ::=  <identifier>
<identifier>   ::=  [A-Za-z0-9_]+
<value>        ::=  <string> | <number> | "TRUE" | "FALSE" | "NULL"
<string>       ::=  "'" <character>* "'" | '"' <character>* '"'
<number>       ::=  [0-9]+ [ "." [0-9]+ ]?
```

Identifiers are case-insensitive. Database names are stored lowercase. Reserved keywords (USE, CREATE, DATABASE, TABLE, DROP, ALTER, ADD, INSERT, INTO, VALUES, SELECT, DISTINCT, FROM, WHERE, UPDATE, SET, DELETE, JOIN, LEFT, AND, ON, LIKE, TRUE, FALSE, NULL, ORDER, BY, ASC, DESC, GROUP, COUNT, SUM, AVG, LIMIT, OFFSET, IS, NOT) cannot be used as identifiers.
