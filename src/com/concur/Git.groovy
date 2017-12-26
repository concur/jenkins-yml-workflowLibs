#!/usr/bin/env groovy
package com.concur

import groovy.transform.Field;

@Field def concurPipeline = new Commands()

// Get the commit SHA for the last file or folder changed.
def getCommitSHA(String folder='.', int depth=1) {
  if (isUnix()) {
    return sh(returnStdout: true, script: "git log -n ${depth} --pretty=format:%H ${folder}").trim()
  } else {
    bat "git log -n ${depth} --pretty=format:%%H ${folder} > lastSHA"
    return readFile('lastSHA')
  }
}

def getFilesChanged(String commitSha=env.GIT_COMMIT) {
  def gitCommand = "git diff-tree --no-commit-id --name-only -r ${env.GIT_COMMIT}"
  return runGitShellCommand(gitCommand, gitCommand, 'files_changed.tmp').trim().tokenize('\n')
}

def runGitShellCommand(String gitCommand, String winGitCommand, String outfileName = 'file.tmp') {
  if (winGitCommand == null) {
    winGitCommand = gitCommand
  }
  if(isUnix()) {
    def command = "${gitCommand} > ${outfileName}"
    concurPipeline.debugPrint(['command': command])
    sh command
  } else {
    def pwd = pwd()
    def command = "[System.IO.File]::WriteAllLines('${pwd}\\${outfileName}', \$(${winGitCommand}))"
    concurPipeline.debugPrint(['command': command])
    bhPsh command
  }
  def fileContents = readFile outfileName
  concurPipeline.debugPrint(["fileContents": fileContents])
  return fileContents
}

// Save git properties to environment variables
def saveGitProperties(Map scmVars) {
  concurPipeline.debugPrint("Getting info on Git commit")

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
      env."${k}" = v
    }
  }

  env.GIT_PREVIOUS_COMMIT = getCommitSHA('.')
}

/*
 * Get the Git org, repo and host
 *
 * String parameter:
 *
 *   @param url -  URL of the Git repository
*/
def getGitData(String url = '') {
  if (!url) {
    url = scm.remoteRepositories[0].uris[0].toString()
  }
  def org
  def repo
  def gitHost
  if (url.startsWith('https://')) {
    def gitUrl = new java.net.URI(url)
    def scmList = gitUrl.getPath().toString().replaceAll(/\.git|\//,' ').split(' ')
    host  = gitUrl.host
    org   = scmList[1]
    repo  = scmList[2]
  } else if (url.startsWith('git@')) {
    return getGitData(url.replace(':', '/').replace('git@', 'https://'))
  } else {
    error("Provided URI is not Git compatible: ${url}")
  }

  return [
    'host': host,
    'org' : org,
    'repo': repo
  ]
}

def getVersion(String version = '0.1.0', String scheme = "semantic", Boolean ignorePrevious = false) {
  if (env."${Constants.Env.VERSION}") {
    println "Returning previously determined version."
    return env."${Constants.Env.VERSION}"
  }
  try {
    def outfileName = "version.tmp"
    def gitCommand = 'git tag --sort -v:refname | head -1'
    def winGitCommand = "\$(${gitCommand})[0]"

    def tag = runGitShellCommand(gitCommand, winGitCommand, outfileName)

    def buildNumber = timeSinceLatestTag()
    if (tag == null || tag.size() == 0) {
      println "no existing tag found using version ${version}-${buildNumber}"
      env."${Constants.Env.VERSION}" = "${version}-${buildNumber}"
      return "${version}-${buildNumber}"
    }
    // Getting the tag to check versioning scheme
    tag = tag.replaceAll("\\s+","")

    println "Tag: ${tag}"
    println """Testing to see what versioning scheme is used on the previous tag and compare that to the ${version} option passed in
          To force a different scheme than what was previously used, set the ignorePrevious option to true."""

    String semverPatternString = '(?i)\\b(?<prefix>v)?(?<major>0|[1-9]\\d*)(?:\\.(?<minor>0|[1-9]\\d*)(?:\\.(?<patch>0|[1-9]\\d*))?)?(?:-(?<prerelease>[\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?(?:\\+(?<build>[\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?\\b'
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(semverPatternString)
    // List of different versioning scheme regex patters to match against
    def semver = pattern.matcher(tag)
    println "Semver: " + semver
    println "Semver matches: " + semver.matches()

    // Checks to see if the version is compatible with Semantic versioning.
    if (semver.matches()) { //new
      println "Version ${tag} is semver compatible"
      def tagPrefix = semver.group('prefix') ?: ''
      def tagMajorVersion = semver.group('major') as int
      def tagMinorVersion = ((semver.group('minor') ?: -1) as int) + 1 //Setting the value to -1 allows for a zero version
      def tagPatchVersion = (semver.group('patch') ?: 0) as int
      def retVersion = "${tagPrefix}${tagMajorVersion}.${tagMinorVersion}.${tagPatchVersion}-${buildNumber}"

      println "Testing to see if current version ${version} is semver compatible"

      def ver = pattern.matcher(version.trim())
      if (ver.matches() && (version != '0.1.0')) {
        println "Current version ${version} is semver compatible"
        def prefix = ver.group('prefix') ?: ''
        def majorVersion = ver.group('major') as int
        def minorVersion = (ver.group('minor') ?: 0) as int
        def patchVersion = (ver.group('patch') ?: 0) as int

        if (majorVersion > tagMajorVersion ||
          (majorVersion == tagMajorVersion &&
            (minorVersion > tagMinorVersion) || (minorVersion == tagMinorVersion && patchVersion > tagPatchVersion)
          )
        ) {
          println "Version is now ${version}. Using what was passed in."
          retVersion = "${prefix}${majorVersion}.${minorVersion}.${patchVersion}-${buildNumber}"
        }
      }
      env."${Constants.Env.VERSION}" = retVersion
      return retVersion
    }
  } catch (e) {
    error("""
    |Unable to version. Please make sure your version type is correct and that you passed in the correct parameters.\n
    |### Parameter Usage ###
    |scheme - The versioning scheme to use, i.e. semantic, alphanumeric, date, etc...
    |version - Version to use in this release, assuming that you don't want auto incrementing (Not implemented yet)
    |ignorePrevious - Don't look at the last tag released and compare it to make sure you're incrementing (Not |implemented yet)\n
    |### Parameters Used ###
    |scheme: ${scheme}
    |version: ${version}
    |ignorePrevious: ${ignorePrevious}
    """.stripMargin())
  }
}

def timeSinceLatestTag() {
  def outfileName = 'tags_date.txt'
  def linuxGitCommand = 'git log --pretty="format:%ci" $(git tag --sort -v:refname) | head -1'
  def winGitCommand = '$(git log --pretty="format:%ci" $(git tag --sort -v:refname))[0]'
  def tagDateString = runGitShellCommand(linuxGitCommand, winGitCommand, outfileName)

  concurPipeline.debugPrint(["Git tag data": tagDateString])

  def tagDate = new com.concur.Util().dateFromString(tagDateString)
  def now = new Date()

  def duration = groovy.time.TimeCategory.minus(now, tagDate)
  concurPipeline.debugPrint([
    "duration": duration,
    "type": duration.getClass()
  ])
  // pad these so that the length is consistent, this should also make things easier to read
  def chunkedMilliseconds = duration.toMilliseconds().toString()
  return chunkedMilliseconds
}
