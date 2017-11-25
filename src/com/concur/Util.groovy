#!/usr/bin/env groovy
package com.concur

// JSON
def parseJSON(stringContent) {
  assert stringContent : 'Unable to use parseJSON with no content'
  def utilityStepsAvailable = new com.concur.Commands().getPluginVersion('pipeline-utility-steps') ?: false
  if (utilityStepsAvailable) {
    return readJSON(text: stringContent)
  } else {
    return new groovy.json.JsonSlurperClassic().parseText(stringContent)
  }
}

def toJSON(content) {
  assert content : "Nothing provided to convert to JSON, this should be any Array/List or Map."
  def jsonOutput = groovy.json.JsonOutput.toJson(content)
  return jsonOutput
}

// YAML
def parseYAML(stringContent) {
  assert stringContent : 'Unable to use parseYAML with no content'
  def utilityStepsAvailable = new com.concur.Commands().getPluginVersion('pipeline-utility-steps') ?: false
  assert utilityStepsAvailable : "Please ensure the [Pipeline Utility Steps] plugin is installed in Jenkins."
  return readYaml(text: stringContent)
}