#!/usr/bin/env groovy
package com.concur

import groovy.transform.Field

import java.util.regex.Pattern;

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
description: |
  Determine a version number based on the current latest tag in the repository. Will automatically increment the minor version and append a build version.
  You can indicate how to increment the semantic version in your pipelines.yml file:
  ```yaml
  pipelines:
    general:
      version:
        increment: # all of these nodes can be either a static boolean or a map matching the patterns from tools.git.patterns
          major: true
          minor:
            master: true
            feature: false
          patch:
            master: false
            feature: true
  ```
examples:
  - |
    // Latest tag in the repo is 1.3.1 and it was tagged 5 hours ago
    println new com.concur.Git().getVersion(yml)
    // 1.4.0-0018000000
  - |
    // New repo with no tags, repository was created 1 hour ago
    println new com.concur.Git().getVersion(yml)
    // 0.1.0-0003600000
  - |
    // No tags in repo, override default version, created 18 days ago
    println new com.concur.Git().getVersion(yml)
    // 3.7.0-1555200000
 */
def getVersion(Map yml) {
  if (env."${Constants.Env.VERSION}") {
    concurPipeline.debugPrint('Returning previously determined version.', 3)
    return env."${Constants.Env.VERSION}"
  }
  try {
    String branchPattern    = concurPipeline.checkBranch(yml)
    String version          = yml.general?.version?.base    ?: '0.1.0'
    String scheme           = yml.general?.version?.scheme  ?: 'semantic'
    Boolean incrementMajor  = yml.general?.version?.increment?.major?."${branchPattern}" ?: yml.general?.version?.increment?.major ?: false
    Boolean incrementMinor  = yml.general?.version?.increment?.minor?."${branchPattern}" ?: yml.general?.version?.increment?.minor ?: false
    Boolean incrementPatch  = yml.general?.version?.increment?.patch?."${branchPattern}" ?: yml.general?.version?.increment?.patch ?: false
    String tag = runGitShellCommand(
      "git describe --tag --abbrev=0 ${env.GIT_COMMIT} | head -1",
      "\$(git describe --tag --abbrev=0 ${env.GIT_COMMIT})[0]"
    )

    def buildNumber = timeSinceTag(tag)
    if (tag == null || tag.size() == 0) {
      def tmpVer = "$version-$buildNumber"
      println "no existing tag found using version: $tmpVer"
      env."${Constants.Env.VERSION}" = tmpVer
      return tmpVer
    }
    // Getting the tag to check versioning scheme
    tag = tag.replaceAll("\\s+","")

    String semverPatternString = '(?i)\\b(?<prefix>v)?(?<major>0|[1-9]\\d*)(?:\\.(?<minor>0|[1-9]\\d*)(?:\\.(?<patch>0|[1-9]\\d*))?)?(?:-(?<prerelease>[\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?(?:\\+(?<build>[\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?\\b'
    Pattern pattern = Pattern.compile(semverPatternString)

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
      String tagPrefix = tagSemver.group('prefix') ?: ''
      List tagVersioning = incrementSemanticVersion(
        (tagSemver.group('major') as int),
        ((tagSemver.group('minor') ?: -1) as int),
        (tagSemver.group('patch') ?: 0) as int,
        incrementMajor,
        incrementMinor,
        incrementPatch
      )
      int tagMajorVersion = tagVersioning[0]
      int tagMinorVersion = tagVersioning[1]
      int tagPatchVersion = tagVersioning[2]
      
      String retVersion = "$tagPrefix$tagMajorVersion.$tagMinorVersion.$tagPatchVersion-$buildNumber"

      if (versionSemver.matches() && (version != '0.1.0')) {
        String prefix = versionSemver.group('prefix') ?: ''
        
        List versioning = incrementSemanticVersion(
          (versionSemver.group('major') as int),
          ((versionSemver.group('minor') ?: 0) as int),
          ((versionSemver.group('patch') ?: 0) as int),
          incrementMajor,
          incrementMinor,
          incrementPatch
        )
        int majorVersion = versioning[0]
        int minorVersion = versioning[1]
        int patchVersion = versioning[2]

        if (majorVersion > tagMajorVersion ||
          (majorVersion == tagMajorVersion &&
            (minorVersion > tagMinorVersion) || (minorVersion == tagMinorVersion && patchVersion > tagPatchVersion)
          )
        ) {
          retVersion = "$prefix$majorVersion.$minorVersion.$patchVersion-$buildNumber"
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
    |scheme: $scheme
    |version: $version
    |### Error ###
    |$e
    """.stripMargin())
  }
}

private incrementSemanticVersion(int major, int minor, int patch, Boolean incMajor, Boolean incMinor, Boolean incPatch) {
  major = incMajor ? major + 1 : major
  minor = incMinor ? minor + 1 : minor
  patch = incPatch ? patch + 1 : 0
  return [major, minor, patch]
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
  def tagDateString = runGitShellCommand(
    "git log --pretty=\"format:%ci\" \$(git rev-list -n 1 $tag) | head -1",
    "\$(git log --pretty=\"format:%ci\" \$(git rev-list -n 1 $tag))[0]"
  )

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
