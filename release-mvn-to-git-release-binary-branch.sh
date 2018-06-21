#!/bin/bash

# Require
# jq
# gitlab client https://github.com/NARKOZ/gitlab

set -e
set -x

API_ENDPOINT=https://gitlab.yeshj.com/api/v3
PROJECT_ID=2523
TARGET=./target/$( ls ./target/ | grep shaded.jar )


REPO_PATH=`git rev-parse --show-toplevel`
REPO_NAME=`basename $REPO_PATH`

mvn clean package
GIT_HASH=`git rev-parse --short HEAD`
TAG=$REPO_NAME-release-binary-`date +%y%m%d.%H%M`-$GIT_HASH
git tag $TAG
git push origin $TAG

JAR_URL_MARKDOWN=$(curl --request POST --header "PRIVATE-TOKEN: $GITLAB_API_PRIVATE_TOKEN" --form "file=@$TARGET" $API_ENDPOINT/projects/$PROJECT_ID/uploads | jq -r ".markdown")
gitlab create_release hjarch-practice/yugong $TAG "Release $JAR_URL_MARKDOWN"

