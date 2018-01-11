#!/usr/bin/env groovy
package com.concur

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

import java.text.SimpleDateFormat

// ########################
// # Date/Time Utils
// ########################

/*
description: default format is to match how a Git tag date is formatted
examples:
  - |
    def dateStr = sh returnStdout: true, script: "git log --pretty="format:%ci" $(git tag --sort -v:refname) | head -1"
    println new com.concur.Util().dateFromString(dateStr)
    // Sun Jan 07 01:37:49 GMT 2018
  - |
    println new com.concur.Util().dateFromString('01-02-2018', 'MM-dd-yyyy')
    // Tue Jan 02 00:00:00 GMT 2018
 */
def dateFromString(String dateString, String format = 'yyyy-MM-dd HH:mm:ss Z') {
  def parsedDate = new SimpleDateFormat(format).parse(dateString)
  new Commands().debugPrint([
    'dateString': dateString,
    'format'    : format,
    'parsedDate': parsedDate
  ])
  return parsedDate
}

// ########################
// # File Utils
// ########################

// JSON
/*
description: Parses the provided string as if it is YAML
examples:
  - |
    println new com.concur.Util().parseJSON('{"content": "JSON content"}')
    // {content=JSON content}
  - |
    println new com.concur.Util().parseJSON(readFile('results.json'))
    // {content=JSON content}
 */
def parseJSON(String stringContent) {
  assert stringContent : 'Unable to use parseJSON with no content'
  def utilityStepsAvailable = new Commands().getPluginVersion('pipeline-utility-steps') ?: false
  if (utilityStepsAvailable) {
    return readJSON(text: stringContent)
  } else {
    return new JsonSlurperClassic().parseText(stringContent)
  }
}

/*
description: Convert the provided content into a valid JSON string
examples:
  - |
    println new com.concur.Util().toJSON(['key1': 'value1', 'key2': 'value2'])
    // {"key1":"value1","key2":"value2"}
  - |
    println new com.concur.Util().toJSON(['item1', 'item2', 'item3', 'item4'])
    // ["item1","item2","item3","item4"]
  - |
    println new com.concur.Util().toJSON('Valid JSON string \'""')
    // "Valid JSON string '\"\""
 */
def toJSON(Object content) {
  assert content : "Nothing provided to convert to JSON, this should be any [String, Array/List or Map]."
  return JsonOutput.toJson(content)
}

// YAML
/*
description: Parses the provided string as if it is YAML
examples:
  - |
    println new com.concur.Util().parseYAML('''
    content: |
      multiline string in YAML
    '''.stripIndent())
    // {content=multiline string in YAML}
  - |
    println new com.concur.Util().parseYAML(readFile('pipelines.yml'))
    // {pipelines={tools={git={...}}}}
 */
def parseYAML(String stringContent) {
  assert stringContent : 'Unable to use parseYAML with no content'
  def utilityStepsAvailable = new com.concur.Commands().getPluginVersion('pipeline-utility-steps') ?: false
  assert utilityStepsAvailable : "Please ensure the [Pipeline Utility Steps] plugin is installed in Jenkins."
  return readYaml(text: stringContent)
}

/*
description: Loads the changelog file specified and gathers the release information. More information about good ways to format changelogs can be found at [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
note: Changelog must have consistent usage of headers and follow markdown standards.
examples:
  - |
    println new com.concur.Util().parseChangelog()
    // {0.2.0=
    // ### Added....
  - |
    println new com.concur.Util().parseChangelog('docs/CHANGELOG.md', '# ')
    // {0.2.0=
    // ### Added....
 */
def parseChangelog(String changelogFile='CHANGELOG.md', String releaseHeader='## ') {
  if (!releaseHeader.endsWith(' ')) {
    releaseHeader += ' '
  }
  String contents = readFile(changelogFile)
  List fileLines = contents.split('\n')
  Map releases = [:]
  int startLine = 0
  for (int i = 0; i <= fileLines.size(); i++) {
    String currLine = fileLines[i]
    if (startLine == 0 && currLine =~ /^${releaseHeader}.+$/) {
        println currLine
        startLine = i
    } else if (startLine != 0 && currLine =~ /^${releaseHeader}.+$/) {
        releases[fileLines[startLine].replace(releaseHeader, '')] = fileLines[startLine+1..i-1].join('\n')
        startLine = i
    } else if (startLine != 0 && i == fileLines.size()) {
        releases[fileLines[startLine].replace(releaseHeader, '')] = fileLines[startLine+1..i-1].join('\n')
    }
  }
  new Commands().debugPrint("Found ${releases.size()} releases in $changelogFile.")
  return releases
}

// ######################
// Linux Helpers
// ######################

/*
description: Checks if a Go binary is installed and if not install it using provided information.
examples:
  - new com.concur.Util().installGoPkg('glide', 'github.com/Masterminds/glide')
  - new com.concur.Util().installGoPkg('dep', 'github.com/golang/dep')
 */
def installGoPkg(String cmd, String repo) {
  new Commands().debugPrint([
    'cmd' : cmd,
    'repo': repo
  ])
  // ensure the repo doesn't contain any extraneous data
  repo = repo.replace('https://', '').replace('git@', '').replace(':', '/').replace('.git', '')
  def installed = binAvailable(cmd)
  if (installed) {
    println "$cmd is not installed, attempting to install via ${repo}..."
    def goAvailable = binAvailable('go')
    if (goAvailable) {
      error("Go is not available so we cannot install $repo")
    }
    sh "go get -v $repo"
  }
}

/*
description: Use which command to determine if a binary/command is available on the linux system
examples:
  - |
    println new com.concur.Util().binAvailable('python')
    // true
  - |
    println new com.concur.Util().binAvailable('go')
    // false
 */
def binAvailable(String bin) {
  return (sh(returnStatus: true, script: "which $bin") > 0)
}

// ######################
// String Manipulation
// ######################

/*
description: convert a string to lower-case kebab-case
examples:
  - |
    println new com.concur.Util().kebab('Jenkins Workflow Libraries')
    // jenkins-workflow-libraries
  - |
    println new com.concur.Util().kebab('alpha_release-0.2.3')
    // alpha-release-0-2-3
 */
def kebab(String s) {
  return s.toLowerCase().replaceAll("[\\W_]+", "-").replaceAll("(^-*|-*\$)", '')
}

/*
description: Replace the last instance of a provided regex with a provided replacement.
examples:
  - |
    new com.concur.Util().replaceLast('0.1.0.32984', /\./, '-')
    // 0.1.0-32984
 */
def replaceLast(String text, String regex, String replacement) {
  return text.replaceFirst("(?s)"+regex+"(?!.*?"+regex+")", replacement);
}

// Text Replacement/Transformations
private addCommonReplacements(providedOptions) {
  // this will replace the existing map with everything from providedOptions
  def version = new Git().getVersion()
  return ([
    'BUILD_VERSION' : version,
    'SHORT_VERSION' : version.split('-')[0],
    'TIMESTAMP'     : new Date().format(env."${Constants.Env.DATE_FORMAT}" ?: 'yyyyMMdd-Hmmss')
  ] << env.getEnvironment() << providedOptions)
}

/*
description: Replace text in a provided String that contains mustache style templates.
examples:
  - |
    println new com.concur.Util().mustacheReplaceAll('Hello {{ git_owner }}')
    // Hello Concur
  - |
    println new com.concur.Util().mustacheReplaceAll('{{ non_standard }} | {{ git_repo }}', ['non_standard': 'This is not provided as an environment variable'])
    // This is not provided as an environment variable | jenkins-yml-workflowLibs
 */
def mustacheReplaceAll(String str, Map replaceOptions=[:]) {
  if (!str) { return "" }
  replaceOptions = addCommonReplacements(replaceOptions)
  new Commands().debugPrint(['replacements': replaceOptions, 'originalString': str], 2)
  replaceOptions.each { option ->
    // if the value is null do not attempt a replacement
    if (option.value) {
      def pattern = ~/\{\{(?: )?(?i)${option.key}(?: )?\}\}/
      str = str.replaceAll(pattern, option.value)
    }
  }
  return str
}
