# BNF Grammar — Java Relational Database

## Top-level command dispatch

```
<command>      ::=  <use> | <create> | <drop> | <alter> | <insert> | <select> | <update> | <delete> | <join>
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
<select>       ::=  "SELECT" <selector> "FROM" <name> [ "WHERE" <condition> ]
<selector>     ::=  "*" | <name-list>
```

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
```

Performs an inner join. Output columns are prefixed with their source table name (e.g., `users.name`, `orders.product`).

## Conditions

```
<condition>    ::=  <comparison> | <condition> "AND" <condition> | <condition> "OR" <condition>
<comparison>   ::=  <name> <operator> <value>
<operator>     ::=  "==" | "!=" | ">" | "<" | ">=" | "<=" | "LIKE"
```

- `LIKE` supports `%` as a wildcard (converted to `.*` internally).
- Numeric values are compared numerically when both sides parse as doubles.
- String values are compared lexicographically.
- Quoted values (single or double) have their quotes stripped before comparison.

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

Identifiers are case-insensitive. Database names are stored lowercase. Reserved keywords (USE, CREATE, DATABASE, TABLE, DROP, ALTER, ADD, INSERT, INTO, VALUES, SELECT, FROM, WHERE, UPDATE, SET, DELETE, JOIN, AND, ON, LIKE, TRUE, FALSE, NULL) cannot be used as identifiers.
