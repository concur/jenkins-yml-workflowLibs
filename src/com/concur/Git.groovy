#!/usr/bin/env groovy
package com.concur

import groovy.transform.Field

@Field def concurPipeline = new Commands()

/*
description: Get the commit SHA for the last file or folder changed.
 */
def getCommitSHA(String folder='.', int depth=1) {
  return runGitShellCommand("git log -n $depth --pretty=format:%H $folder")
}

/*
description: Get a list of files that were changed in the current commit.
 */
def getFilesChanged(String commitSha='') {
  if (!commitSha) { commitSha = env.GIT_COMMIT }
  return runGitShellCommand("git diff-tree --no-commit-id --name-only -r ${commitSha}").tokenize('\n')
}

/*
description: Run a command, set the command for Linux and Windows and this method will determine which one to use.
 */
def runGitShellCommand(String gitCommand, String winGitCommand='') {
  if (!winGitCommand) {
    winGitCommand = gitCommand
  }
  concurPipeline.debugPrint([
    'gitCommand'    : gitCommand,
    'winGitCommand' : winGitCommand
  ], 2)
  if(isUnix()) {
    return sh(returnStdout: true, script: gitCommand).trim()
  } else {
    return powershell(returnStdout: true, script: winGitCommand).trim()
  }
}

/*
description: Save git properties to environment variables
example: |
  new com.concur.Git().saveGitProperties()
  sh "env"
  // GIT_SHORT_COMMIT=b828c9
  // GIT_COMMIT=b828c94aba486ac0416bf95e387d860b79e6343f
  // GIT_URL=git@github.com:concur/jenkins-yml-workflowLibs
  // GIT_COMMIT_MESSAGE=Fix workflow version lock.
  // GIT_AUTHOR=Nic Patterson
  // GIT_EMAIL=arasureynn@gmail.com
  // GIT_PREVIOUS_COMMIT=597563389d144c7098dd3b71b1fc1e600b215ff7
  // GIT_OWNER=concur
  // GIT_HOST=github.com
  // GIT_REPO=jenkins-yml-workflowLibs
  // ....
 */
def saveGitProperties(Map scmVars) {
  concurPipeline.debugPrint("Getting info on Git commit")

  gitCommands = [
    'GIT_SHORT_COMMIT'    : 'git rev-parse --short HEAD',
    'GIT_URL'             : 'git config --get remote.origin.url',
    'GIT_COMMIT_MESSAGE'  : 'git log -n 1 --pretty=format:%s',
    'GIT_AUTHOR'          : 'git show -s --pretty=%an',
    'GIT_EMAIL'           : 'git log -1 --pretty=%cE'
  ]

  concurPipeline.jenkinsMap(gitCommands).each {
    env."${it.key}" = runGitShellCommand(it.value)
  }

  def gitData = getGitData()

  gitData.each {
    env."GIT_${it.key.toUpperCase()}" = it.value
  }

  if (scmVars) {
    /** from `checkout scm` should provide the following
      * GIT_BRANCH
      * GIT_COMMIT
      * GIT_PREVIOUS_COMMIT
      * GIT_PREVIOUS_SUCCESSFUL_COMMIT
      * GIT_URL
      */
    concurPipeline.debugPrint(['scmVars': scmVars])
    scmVars.each { k, v ->
      env."$k" = v
    }
  }

  env.GIT_PREVIOUS_COMMIT = getCommitSHA('.')
}

/*
description: Get the Git owner, repo and host
examples:
  - |
    println new com.concur.Git().getGitData('https://github.com/concur/jenkins-yml-workflowLibs.git')
    // ['host': 'github.com', 'owner': 'concur', 'repo': 'jenkins-yml-workflowLibs']
  - |
    println new com.concur.Git().getGitData('https://github.example.com/awesome/repo.git')
    // ['host': 'github.example.com', 'owner': 'awesome', 'repo': 'repo']
*/
def getGitData(String url = '') {
  if (!url) {
    url = scm.remoteRepositories[0].uris[0].toString()
  }
  def owner
  def repo
  def gitHost
  if (url.startsWith('https://')) {
    def gitUrl = new URI(url)
    def scmList = gitUrl.getPath().toString().replaceAll(/\.git|\//,' ').split(' ')
    gitHost  = gitUrl.host
    owner = scmList[1]
    repo  = scmList[2]
  } else if (url.startsWith('git@')) {
    return getGitData(url.replace(':', '/').replace('git@', 'https://'))
  } else {
    error("Provided URI is not Git compatible: ${url}")
  }

  return [
    'host'  : gitHost,
    'owner' : owner,
    'repo'  : repo
  ]
}

/*
description: Get the amount of time since the last Git tag was created.
examples:
  - |
    // Last tag was 3 hours ago
    println new com.concur.Git().timeSinceTag('v3.1.0')
    // 0010800000 - Padded with 0s on the left.
  - |
    // last tag was 6 months ago
    println new com.concur.Git().timeSinceTag('v0.1.0')
    // 1555200000 - Chunked to keep it at 10 characters
 */
def timeSinceTag(String tag) {
  String tagDateString = ""
  if (!tag) {
    // This will get the initial commit of the repository (most likely)
    tagDateString = runGitShellCommand(
      "git rev-list --max-parents=0 HEAD"
    )
  }
  tagDateString = runGitShellCommand(
    "git log --pretty=\"format:%ci\" \$(git rev-list -n 1 $tag)"
  ).split('\n')[0]

  concurPipeline.debugPrint(["Git tag data": tagDateString])

  def tagDate = new Util().dateFromString(tagDateString)
  def now = new Date()

  def duration = groovy.time.TimeCategory.minus(now, tagDate)
  concurPipeline.debugPrint([
    "duration": duration,
    "type": duration.getClass()
  ])
  // pad these so that the length is consistent, this should also make things easier to read
  def chunkedMilliseconds = duration.toMilliseconds().toString()
  if (chunkedMilliseconds.size() > 10) {
    return chunkedMilliseconds[0..9]
  } else {
    return chunkedMilliseconds.padLeft(10, '0')
  }
}
