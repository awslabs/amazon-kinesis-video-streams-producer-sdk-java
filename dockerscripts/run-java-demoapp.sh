#!/bin/bash
# Demo run of Java SDK application for sending video streams
# within the docker container
# 
if [ "$#" != 3 ]; then
 echo " Usage: ./run-java-demoapp.sh access_key secret_key kvs_stream"
 exit
fi
ACCESS_KEY=$1
SECRET_KEY=$2
KVS_STREAM=$3
mvn package
# Create a temporary filename in /tmp directory
jar_files=$(mktemp)
# Create classpath string of dependencies from the local repository to a file
mvn -Dmdep.outputFile=$jar_files dependency:build-classpath
export LD_LIBRARY_PATH=/opt/amazon-kinesis-video-streams-producer-sdk-cpp/open-source/local/lib:$LD_LIBRARY_PATH
classpath_values=$(cat $jar_files)
# Start the demo app
java -classpath target/amazon-kinesis-video-streams-producer-sdk-java-1.10.0.jar:$classpath_values -Daws.accessKeyId=${ACCESS_KEY} -Daws.secretKey=${SECRET_KEY} Dkvs-stream=${KVS_STREAM} -Djava.library.path=/opt/amazon-kinesis-video-streams-producer-sdk-cpp/build/ com.amazonaws.kinesisvideo.demoapp.DemoAppMain