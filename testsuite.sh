#!/bin/bash

mkdir -p REPORT
for i in {1..100}
do
   mvn clean package | tee "build.log"
   mkdir -p REPORT/round-$i
   cp -rf target/surefire-reports REPORT/round-$i
   mv -f build.log REPORT/round-$i/build.log
done
