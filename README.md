## Amazon Kinesis Video Streams Producer SDK Java

## License

This library is licensed under the Amazon Software License.

## Introduction

The Amazon Kinesis Video Streams Producer SDK Java allows developers to install and customize their connected camera and other devices to securely stream video, audio, and time-encoded data to Kinesis Video Streams.

### Building from Source

Import the Maven project to your IDE, it will find dependency packages from Maven and build.

### Lauching sample application
Run DemoAppMain.java in ./src/main/demo with JVM arguments set to "-Daws.accessKeyId={YourAwsAccessKey} -Daws.secretKey={YourAwsSecretKey} -Djava.library.path={NativeLibraryPath}" or "-Daws.accessKeyId={YourAwsAccessKey} -Daws.secretKey={YourAwsSecretKey} -Daws.sessionToken={YourAwsSessionToken} -Djava.library.path={NativeLibraryPath}". Then demo app will start running and putting demo video into Kinesis Video Streams. You can change your stream settings in DemoAppMain.java before you run the app.

## Release Notes

### Release 1.0.0 (November 2017)

First release of the Amazon Kinesis Video Producer SDK Java.
