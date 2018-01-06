#!/usr/bin/env groovy
package com.concur

import groovy.transform.Field;

@Field def concurPipeline = new Commands()

// Get the commit SHA for the last file or folder changed.
def getCommitSHA(String folder='.', int depth=1) {
  return runGitShellCommand("git log -n ${depth} --pretty=format:%H ${folder}")
}

def getFilesChanged(String commitSha='') {
  if (!commitSha) { commitSha = env.GIT_COMMIT }
  return runGitShellCommand("git diff-tree --no-commit-id --name-only -r ${commitSha}").tokenize('\n')
}

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

// Save git properties to environment variables
def saveGitProperties(Map scmVars) {
  concurPipeline.debugPrint("Getting info on Git commit")

  gitCommands = [
    'GIT_SHORT_COMMIT'    : 'git rev-parse --short HEAD',
    'GIT_COMMIT'          : 'git rev-parse HEAD',
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
      env."${k}" = v
    }
  }

  env.GIT_PREVIOUS_COMMIT = getCommitSHA('.')
}

/*
 * Get the Git owner, repo and host
 *
 * String parameter:
 *
 *   @param url -  URL of the Git repository
*/
def getGitData(String url = '') {
  if (!url) {
    url = scm.remoteRepositories[0].uris[0].toString()
  }
  def owner
  def repo
  def gitHost
  if (url.startsWith('https://')) {
    def gitUrl = new java.net.URI(url)
    def scmList = gitUrl.getPath().toString().replaceAll(/\.git|\//,' ').split(' ')
    host  = gitUrl.host
    owner = scmList[1]
    repo  = scmList[2]
  } else if (url.startsWith('git@')) {
    return getGitData(url.replace(':', '/').replace('git@', 'https://'))
  } else {
    error("Provided URI is not Git compatible: ${url}")
  }

  return [
    'host'  : host,
    'owner' : owner,
    'repo'  : repo
  ]
}

def getVersion(String version = '0.1.0', String scheme = "semantic", Boolean ignorePrevious = false) {
  if (env."${Constants.Env.VERSION}") {
    concurPipeline.debugPrint('Returning previously determined version.', 3)
    return env."${Constants.Env.VERSION}"
  }
  try {
    def tag = runGitShellCommand('git tag --sort -v:refname | head -1', '$(git tag --sort -v:refname)[0]')

    def buildNumber = timeSinceLatestTag()
    if (tag == null || tag.size() == 0) {
      println "no existing tag found using version: ${version}-${buildNumber}"
      env."${Constants.Env.VERSION}" = "${version}-${buildNumber}"
      return "${version}-${buildNumber}"
    }
    // Getting the tag to check versioning scheme
    tag = tag.replaceAll("\\s+","")

    String semverPatternString = '(?i)\\b(?<prefix>v)?(?<major>0|[1-9]\\d*)(?:\\.(?<minor>0|[1-9]\\d*)(?:\\.(?<patch>0|[1-9]\\d*))?)?(?:-(?<prerelease>[\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?(?:\\+(?<build>[\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?\\b'
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(semverPatternString)

    // List of different versioning scheme regex patters to match against
    def tagSemver     = pattern.matcher(tag)
    def versionSemver = pattern.matcher(version.trim())

    concurPipeline.debugPrint([
      'tag'               : tag,
      'tag is semver'     : tagSemver.matches(),
      'version'           : version,
      'version is semver' : versionSemver.matches()
    ])

    // Checks to see if the version is compatible with Semantic versioning.
    if (tagSemver.matches()) { //new
      def tagPrefix = tagSemver.group('prefix') ?: ''
      def tagMajorVersion = tagSemver.group('major') as int
      def tagMinorVersion = ((tagSemver.group('minor') ?: -1) as int) + 1 //Setting the value to -1 allows for a zero version
      def tagPatchVersion = (tagSemver.group('patch') ?: 0) as int
      def retVersion = "${tagPrefix}${tagMajorVersion}.${tagMinorVersion}.${tagPatchVersion}-${buildNumber}"
      if (versionSemver.matches() && (version != '0.1.0')) {
        def prefix = versionSemver.group('prefix') ?: ''
        def majorVersion = versionSemver.group('major') as int
        def minorVersion = (versionSemver.group('minor') ?: 0) as int
        def patchVersion = (versionSemver.group('patch') ?: 0) as int

        if (majorVersion > tagMajorVersion ||
          (majorVersion == tagMajorVersion &&
            (minorVersion > tagMinorVersion) || (minorVersion == tagMinorVersion && patchVersion > tagPatchVersion)
          )
        ) {
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
  def tagDateString = runGitShellCommand(
    'git log --pretty="format:%ci" $(git tag --sort -v:refname) | head -1',
    '$(git log --pretty="format:%ci" $(git tag --sort -v:refname))[0]'
  )

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
