#!/usr/bin/env bash
java -cp os-hw2.jar\
:/opt/hadoop/share/hadoop/common/*\
:/opt/hadoop/share/hadoop/common/lib/*\
\
:/opt/hadoop/share/hadoop/hdfs/*\
:/opt/hadoop/share/hadoop/hdfs/lib/*\
\
:/opt/hadoop/share/hadoop/mapreduce/*\
\
:/opt/hadoop/share/hadoop/tools/lib/*\
\
:/opt/hadoop/share/hadoop/yarn/*\
:/opt/hadoop/share/hadoop/yarn/lib/*\
 \
nctu.cs.oss.hw2.app.ServerApplication
