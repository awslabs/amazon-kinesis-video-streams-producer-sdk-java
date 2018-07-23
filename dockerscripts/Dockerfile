FROM ubuntu:17.10
RUN apt-get update
RUN apt-get install -y git && \
    apt-get install -y vim  && \
    apt-get install -y curl && \
    apt-get install -y xz-utils && \
    apt-get install -y byacc  && \
    apt-get install -y g++ && \
    apt-get install -y python2.7 && \
    apt-get install -y pkg-config && \
    apt-get install -y cmake && \
    apt-get install -y maven && \
    apt-get install -y openjdk-8-jdk && \
    rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64/
WORKDIR /opt/
# Checkout latest Kinesis Video Streams Producer SDK (CPP)
RUN git clone https://github.com/awslabs/amazon-kinesis-video-streams-producer-sdk-cpp.git
# Uncomment next two lines to debug any custom  install-script
#WORKDIR /opt/amazon-kinesis-video-streams-producer-sdk-cpp/kinesis-video-native-build/
#COPY install-script /opt/amazon-kinesis-video-streams-producer-sdk-cpp/kinesis-video-native-build/
#
# Build the Producer SDK (CPP)
WORKDIR /opt/amazon-kinesis-video-streams-producer-sdk-cpp/kinesis-video-native-build/
RUN  chmod a+x ./install-script
RUN  chmod a+x ./java-install-script
RUN ./install-script -a
RUN ./java-install-script
WORKDIR /opt/
# Checkout latest Kinesis Video Streams Producer SDK (Java)
RUN git clone https://github.com/awslabs/amazon-kinesis-video-streams-producer-sdk-java.git
WORKDIR /opt/amazon-kinesis-video-streams-producer-sdk-java
# Start the demoapplication to send video streams
COPY run-java-demoapp.sh /opt/amazon-kinesis-video-streams-producer-sdk-java/
RUN chmod a+x /opt/amazon-kinesis-video-streams-producer-sdk-java/run-java-demoapp.sh
