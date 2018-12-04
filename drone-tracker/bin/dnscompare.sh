#!/usr/bin/env sh
workdir="$( cd "$(dirname "$0")/.." ; pwd -P )"
cd $workdir
classpath=$workdir/lib/*
exec java -cp "$classpath" DNSLookupCompare $*
