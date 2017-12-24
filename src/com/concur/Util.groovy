#!/usr/bin/env groovy
package com.concur

import org.codehaus.groovy.runtime.GStringImpl

// ########################
// # Date/Time Utils
// ########################

// default format is to match how a Git tag date is formatted
def dateFromString(String dateString, String format = 'yyyy-MM-dd HH:mm:ss Z') {
  def parsedDate = new java.text.SimpleDateFormat(format).parse(dateString)
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
def parseJSON(String stringContent) {
  assert stringContent : 'Unable to use parseJSON with no content'
  def utilityStepsAvailable = new com.concur.Commands().getPluginVersion('pipeline-utility-steps') ?: false
  if (utilityStepsAvailable) {
    return readJSON(text: stringContent)
  } else {
    return new groovy.json.JsonSlurperClassic().parseText(stringContent)
  }
}

def toJSON(Map content) {
  assert content : "Nothing provided to convert to JSON, this should be any [String, Array/List or Map]."
  return groovy.json.JsonOutput.toJson(content)
}

def toJSON(List content) {
  assert content : "Nothing provided to convert to JSON, this should be any [String, Array/List or Map]."
  return groovy.json.JsonOutput.toJson(content)
}

def toJSON(String content) {
  assert content : "Nothing provided to convert to JSON, this should be any [String, Array/List or Map]."
  return groovy.json.JsonOutput.toJson(content)
}

// YAML
def parseYAML(String stringContent) {
  assert stringContent : 'Unable to use parseYAML with no content'
  def utilityStepsAvailable = new com.concur.Commands().getPluginVersion('pipeline-utility-steps') ?: false
  assert utilityStepsAvailable : "Please ensure the [Pipeline Utility Steps] plugin is installed in Jenkins."
  return readYaml(text: stringContent)
}

// convert a string to lower-case kebab-case
def kebab(String s) {
  return s.toLowerCase().replaceAll("[\\W_]+", "-").replaceAll("(^-*|-*\$)", '')
}

def replaceLast(String text, String regex, String replacement) {
  return text.replaceFirst("(?s)"+regex+"(?!.*?"+regex+")", replacement);
}

// Text Replacement/Transformations
private addCommonReplacements(providedOptions) {
  // this will replace the existing map with everything from providedOptions
  return ([
    'BUILD_VERSION' : new com.concur.Git().getVersion(),
    'TIMESTAMP'     : new Date().format(env."${Constants.Env.DATE_FORMAT}" ?: 'yyyyMMdd-Hmmss')
  ] << env.getEnvironment() << providedOptions)
}

def mustacheReplaceAll(str, replaceOptions=[:]) {
  if (!str) { return "" }
  replaceOptions = addCommonReplacements(replaceOptions)
  new Commands().debugPrint(['replacements': replaceOptions, 'originalString': str])
  replaceOptions.each { option ->
    // if the value is null do not attempt a replacement
    if (option.value) {
      def pattern = ~/\{\{(?: )?(?i)${option.key}(?: )?\}\}/
      str = str.replaceAll(pattern, option.value)
    }
  }
  return str
}
