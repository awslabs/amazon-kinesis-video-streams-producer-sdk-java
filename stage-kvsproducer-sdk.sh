#!/bin/bash
# Script to publish to maven
# SOP on how to run this script https://w.amazon.com/bin/view/Acuity/SOP/Publish_Producer_SDK_Java_Maven/
 
 set -e
 
 if [ -z "$1" -a -e "pom.xml" ]; then
     echo "Using $(pwd)/pom.xml"
 elif [ -d $1 ]; then
     echo "Using passed in directory: $1/pom.xml"
     pushd $1
 else
     echo "This must be run in the amazon-kinesis-video-streams-producer-sdk-java directory, or provide the the amazon-kinesis-video-streams-producer-sdk-java on the command line"
     exit 1
 fi
 POM_FILE=$(pwd)/pom.xml
 
 SDK_VERSION=$(echo $POM_FILE | perl -ne 'chomp; use XML::Simple; $r = XMLin($_); print $r->{"version"};')
 echo "Starting build for KVS Producer SDK Java Version $SDK_VERSION"
 
 mvn clean

 echo "Running git secrets"
 
 git secrets --scan -r . || exit 1
 git secrets --scan-history || exit 1
 
 SCRIPT_BASE=$(dirname $0)/../
 echo "Script base: $SCRIPT_BASE"
 
 echo "Cleaning project directory"
 mvn clean
 
 echo "Running verify to build test, and build jar"
 mvn verify
 
 echo "Running javadoc jar build"
 mvn javadoc:jar -Dadditionalparam='-Xdoclint:none'
 
 echo "Running source jar build"
 mvn source:jar
 
 JAR_BASE="target/amazon-kinesis-video-streams-producer-sdk-java-$SDK_VERSION"
 JAR_FILE="$JAR_BASE.jar"
 JAVADOC_FILE="$JAR_BASE-javadoc.jar"
 SOURCE_FILE="$JAR_BASE-sources.jar"
 
 for file in $JAR_FILE $JAVADOC_FILE $SOURCE_FILE; do
     if [ ! -e $file ]; then
         echo "$file doesn't exist."
         exit 1
     fi
 done    
 
 SONATYPE_USER_NAME=$(odin-get -t Principal com.aws.dr.aws-java-sdk.sonatype)
 SONATYPE_PASSWORD=$(odin-get -t Credential com.aws.dr.aws-java-sdk.sonatype)
 
 SONATYPE_REPO_ID=sonatype-nexus-staging
 
 CREATE_REQUEST="
 <promoteRequest>
     <data>
         <description>Amazon Kinesis Video Streams Producer SDK Java v$SDK_VERSION Staging repository</description>
     </data>
 </promoteRequest>"

 echo "Request: "
 echo "$CREATE_REQUEST"
 
 echo -n "Ready to create staging repository. Continue (y/n): "
 read create_staging_repo
 
 if [ "$create_staging_repo" != "y" ]; then
     echo "Didn't get 'y' for creation of staging repository. Exiting" 
     exit 1
 fi
 
 
 STAGING_DATA=$(echo $CREATE_REQUEST | curl -X POST -d @- -u $SONATYPE_USER_NAME:$SONATYPE_PASSWORD -H "Content-Type:application/xml" -v https://oss.sonatype.org/service/local/staging/profiles/76710be2588021/start)
 
 echo "Staging Data: $STAGING_DATA"
 STAGING_REPO_ID=$(echo $STAGING_DATA | perl -ne 'print $1 if m{<stagedRepositoryId>([^<]+)</stagedRepositoryId>};')
 STAGING_REPO_URL="https://oss.sonatype.org/service/local/staging/deployByRepositoryId/$STAGING_REPO_ID"
 echo "Created staging repo: $STAGING_REPO_ID with URL: $STAGING_REPO_URL"
 
 echo -n "Ready to sign, and deploy.  Continue (y/n): "
 read sign_and_deploy
 
 if [ "$sign_and_deploy" != "y" ]; then
     echo "Didn't get 'y' for sign, and deploy.  Exiting"
     exit 1
 fi
 
 # To fix https://github.com/keybase/keybase-issues/issues/2798
 export GPG_TTY=$(tty)
 GPG_KEY_ID=$(odin-get -t Principal com.amazon.aws.kinesis.tools.gpg)
 GPG_KEY_PHRASE=$(odin-get -t Credential com.amazon.aws.kinesis.tools.gpg)
 GPG_HOMEDIR="$SCRIPT_BASE/gpghome"
 SETTINGS_FILE="$SCRIPT_BASE/mvnhome/settings.xml"
 
 mvn gpg:sign-and-deploy-file --settings $SETTINGS_FILE \
     -DpomFile=$POM_FILE \
     -Dfile=$JAR_FILE \
     -Djavadoc=$JAVADOC_FILE \
     -Dsources=$SOURCE_FILE \
     -DrepositoryId=$SONATYPE_REPO_ID \
     -Durl=$STAGING_REPO_URL \
     -Dgpg.keyname=$GPG_KEY_ID \
     -Dgpg.passphrase=$GPG_KEY_PHRASE \
     -Dgpg.homedir=$GPG_HOMEDIR \
     -Dsonatype.password=$SONATYPE_PASSWORD \
     -Dsonatype.username=$SONATYPE_USER_NAME
