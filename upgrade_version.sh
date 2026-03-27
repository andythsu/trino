#!/bin/bash

if [ "$#" -lt 2 ]; then
    echo "Ussage: pass old and then version"
    exit 1
fi

echo "Old version: $1, new version $2"

find . -name "pom.xml" -exec sed -i -e "s/$1/$2/g" {} +
