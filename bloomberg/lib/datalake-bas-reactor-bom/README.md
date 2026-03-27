# datalake-bas-reactor-bom

This BOM manages the dependency versions for `bas-reactor`. Since `bas-reactor`
is used to resolve Bloomberg-specific libraries, version conflicts can 
occasionally arise between `bas-reactor` and `Trino`. To address such collisions, 
the `pom.xml` provided here will act as the parent POM for all bas-related 
libraries.