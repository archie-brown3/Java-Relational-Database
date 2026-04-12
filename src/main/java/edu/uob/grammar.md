<command>      ::= <insert> | <select> | <delete>

<insert>       ::= "INSERT" "INTO" <table> "VALUES" "(" <value-list> ")"
<select>       ::= "SELECT" <selector> "FROM" <table> [ "WHERE" <condition> ]
<delete>       ::= "DELETE" "FROM" <table> "WHERE" <condition>

<selector>     ::= "*" | <name-list>
<condition>    ::= <name> "=" <value>

<name-list>    ::= <name> | <name> "," <name-list>
<value-list>   ::= <value> | <value> "," <value-list>

<table>        ::= <name>
<name>         ::= <identifier>
<value>        ::= <string> | <number> | "TRUE" | "FALSE" | "NULL"



