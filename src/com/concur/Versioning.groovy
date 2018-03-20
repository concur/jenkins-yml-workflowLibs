#!/usr/bin/env groovy
package com.concur

import groovy.transform.Field

@Field def concurPipeline = new Commands()
@Field def concurUtil     = new Util()

/*
description: |
  Determine a version number based on the current latest tag in the repository. Will automatically increment the minor version and append a build version.
  ```yaml
  pipelines:
    general:
      version:
        base: 1.0.0
        scheme: semantic
        versionImage: quay.io/reynn/docker-versioner:0.1.0
        pattern: "^.*.*"
  ```
examples:
  - |
    // Latest tag in the repo is 1.3.1 and it was tagged 5 hours ago
    println new com.concur.Versioning().getVersion(yml)
    // 1.4.0-0018000000
  - |
    // New repo with no tags, repository was created 1 hour ago
    println new com.concur.Versioning().getVersion(yml)
    // 0.1.0-0003600000
  - |
    // No tags in repo, override default version, created 18 days ago
    println new com.concur.Versioning().getVersion(yml)
    // 3.7.0-1555200000
 */
def getVersion(Map yml) {
  if (env."${Constants.Env.VERSION}") {
    concurPipeline.debugPrint('Returning previously determined version.', 3)
    return env."${Constants.Env.VERSION}"
  }
  Map versioningData    = [
    'image'     : 'quay.io/reynn/docker-versioner:0.3.0',
    'executable': 'versioning'
  ] << (yml.general?.version ?: [:])

  String dockerImage    = versioningData?.image
  String executable     = versioningData?.executable
  
  List envs = concurUtil.mustacheReplaceAll(versioningData.collect{
    "version_${it.key}=${concurUtil.mustacheReplaceAll(it.value)}"
  }.join(';')).split(';') ?: []

  concurPipeline.debugPrint(['data': versioningData, 'envs': envs])
  
  String returnVal = ''

  assert dockerImage  : '[image] must be provided under general.version.'
  assert executable   : '[executable] must be provided under general.version.'

  withEnv(envs) {
    sh "printenv"
    returnVal = sh(returnStdout: true, script: "docker run $dockerImage $executable")
    // docker.image(dockerImage).inside {
    //   returnVal = sh(returnStdout: true, script: executable).trim()
    // }
  }
  env."${Constants.Env.VERSION}" = returnVal
  return output
}
