### Sample Docker container build and run instructions for Kinesis Video Streams Java SDK demo application
#### Install Docker

Follow instructions to download and start Docker

* [ Docker download instructions ]( https://www.docker.com/community-edition#/download )
* [Getting started with Docker](https://docs.docker.com/get-started/)

#### Build Docker image
Download the `Dockerfile` and `run-java-demoapp.sh` into a folder.    Once the Docker is installed and running, you can then build the docker image by using the following command.

```
  $ docker build -t kvsjavasdkdemoimage .
```
* Get the image id from the previous step (once the build is complete) by running the command `docker images` which will display the Docker images built in your system.

```
  $ docker images
```

* Use the **IMAGE_ID** from the output of the previous command (e.g `f97f1a633597` ) :

```
    REPOSITORY            TAG                 IMAGE ID            CREATED             SIZE
  kvsjavasdkdemoimage   latest              f97f1a633597        14 minutes ago      2.85GB

```
#### Start the Docker container 
---

*  Start the Kinesis Video Streams JDK Docker container using the following command:
```
   $docker run -it <image_id> bash
```
* Example:
```
  $docker run -it f97f1a633597  bash
```

___

 Now that you are within the container, you will see your terminal looks similar to the one below .

* Example:
```
   root@5ddd53df8e6b:/opt/amazon-kinesis-video-streams-producer-sdk-java# ls
   LICENSE  NOTICE  README.md  pom.xml  run-java-demoapp.sh  src
```

#### Start the demo application

* You can now start the demo application using the script (`run-java-demoapp.sh `) as below with your secret key and access_key.

```
   #./run-java-demoapp.sh <ACCESS_KEY> <SECRET_KEY>
```
