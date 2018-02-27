#!/bin/bash
# Demo run of Java SDK application for sending video streams
# within the docker container
# 
if [ "$#" != 2 ]; then
 echo " Usage: ./run-java-demoapp.sh access_key secret_key "
 exit
fi
ACCESS_KEY=$1
SECRET_KEY=$2
mvn package
# Create a temporary filename in /tmp directory
jar_files=$(mktemp)
# Create classpath string of dependencies from the local repository to a file
mvn -Dmdep.outputFile=$jar_files dependency:build-classpath
export LD_LIBRARY_PATH=/opt/amazon-kinesis-video-streams-producer-sdk-cpp/kinesis-video-native-build/downloads/local/lib:$LD_LIBRARY_PATH
classpath_values=$(cat $jar_files)
# Start the demo app
java -classpath target/kinesisvideo-java-demo-1.0-SNAPSHOT.jar:$classpath_values -Daws.accessKeyId=${ACCESS_KEY} -Daws.secretKey=${SECRET_KEY} -Djava.library.path=/opt/amazon-kinesis-video-streams-producer-sdk-cpp/kinesis-video-native-build com.amazonaws.kinesisvideo.demoapp.DemoAppMain
