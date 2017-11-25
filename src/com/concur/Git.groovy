package com.concur

import groovy.transform.Field;

@Field def concurPipeline = new Commands()

// Get the commit SHA for the last file or folder changed.
def getCommitSHA(folder) {
  if (isUnix()) {
    return sh(returnStdout: true, script: "git log -n 1 --pretty=format:%H ${folder}").trim()
  } else {
    bat "git log -n 1 --pretty=format:%%H ${folder} > lastSHA"
    return readFile('lastSHA')
  }
}

def getFilesChanged(commitSha=env.GIT_COMMIT) {
  def gitCommand = "git diff-tree --no-commit-id --name-only -r ${env.GIT_COMMIT}"
  return runGitShellCommand(gitCommand, gitCommand, 'files_changed.tmp').trim().tokenize('\n')
}

def runGitShellCommand(gitCommand, winGitCommand, outfileName = 'file.tmp') {
  if (winGitCommand == null) {
    winGitCommand = gitCommand
  }
  if(isUnix()) {
    def command = "${gitCommand} > ${outfileName}"
    concurPipeline.debugPrint("WorkflowLibs :: Git :: runGitShellCommand :: Linux", ['command': command])
    sh command
  } else {
    def pwd = pwd()
    def command = "[System.IO.File]::WriteAllLines('${pwd}\\${outfileName}', \$(${winGitCommand}))"
    concurPipeline.debugPrint("WorkflowLibs :: Git :: runGitShellCommand :: Windows", ['command': command])
    bhPsh command
  }
  def fileContents = readFile outfileName
  concurPipeline.debugPrint("WorkflowLibs :: Git :: readFile in runGitShellCommand", ["fileContents": fileContents])
  return fileContents
}

// Save git properties to environment variables
def saveGitProperties(scmVars) {
  concurPipeline.debugPrint('WorkflowLibs :: Git :: saveGitProperties', "Getting info on Git commit")

  gitCommands = [
    'GIT_SHORT_COMMIT'    : 'git rev-parse --short HEAD',
    'GIT_COMMIT'          : 'git rev-parse HEAD',
    'GIT_URL'             : 'git config --get remote.origin.url',
    'GIT_COMMIT_MESSAGE'  : 'git log -n 1 --pretty=format:%s',
    'GIT_AUTHOR'          : 'git show -s --pretty=%an'
  ]

  if (isUnix()) {
    concurPipeline.jenkinsMap(gitCommands).each {
      env."${it.key}" = sh(returnStdout: true, script: "${it.value}").trim()
    }
  } else {
    concurPipeline.jenkinsMap(gitCommands).each {
      bat "${it.value.replace('%', '%%')} > ${it.key}"
      env."${it.key}" = readFile("${it.key}").trim()
    }
  }

  if (scmVars) {
    /** from `checkout scm` should provide the following
      * GIT_BRANCH
      * GIT_COMMIT
      * GIT_PREVIOUS_COMMIT
      * GIT_PREVIOUS_SUCCESSFUL_COMMIT
      * GIT_URL
      */
    concurPipeline.debugPrint('WorkflowLibs :: Git :: saveGitProperties', ['scmVars': scmVars])
    scmVars.each { k, v ->
      env."${k}" = v
    }
  }
}
