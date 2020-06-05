FROM ubuntu:18.04
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
    apt-get install -y m4 && \
    apt-get install -y pkg-config && \
    rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64/
WORKDIR /opt
# Checkout latest Kinesis Video Streams Producer SDK (CPP)
RUN git clone https://github.com/awslabs/amazon-kinesis-video-streams-producer-sdk-cpp.git
# Build the Producer SDK (CPP)

WORKDIR /opt/amazon-kinesis-video-streams-producer-sdk-cpp/
RUN git submodule update --init
RUN mkdir -p /opt/amazon-kinesis-video-streams-producer-sdk-cpp/build
WORKDIR /opt/amazon-kinesis-video-streams-producer-sdk-cpp/build
RUN cmake .. -DBUILD_JNI=TRUE
RUN make

WORKDIR /opt/
# Checkout latest Kinesis Video Streams Producer SDK (Java)
RUN git clone https://github.com/awslabs/amazon-kinesis-video-streams-producer-sdk-java.git
WORKDIR /opt/amazon-kinesis-video-streams-producer-sdk-java
# Start the demoapplication to send video streams
COPY run-java-demoapp.sh /opt/amazon-kinesis-video-streams-producer-sdk-java/
RUN chmod a+x /opt/amazon-kinesis-video-streams-producer-sdk-java/run-java-demoapp.sh
