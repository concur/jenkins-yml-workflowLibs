#!/usr/bin/env groovy
package com.concur

/*
description: Adds a key/value to a URL and ensures it is formatted appropriately.
example: |
  println new com.concur.Http().addToUriQueryString('https://example.com/api', 'a', 'd')
  // https://example.com/api?a=d
 */
def addToUriQueryString(String uri, String k, String v) {
  String key = java.net.URLEncoder.encode(k, "UTF-8")
  String value = java.net.URLEncoder.encode(v, "UTF-8")
  return uri.contains('?') ? "${uri}&${key}=${value}" : "${uri}?${key}=${value}"
}

/*
description: Appends the provided Map to the URL with appropriate HTTP formatting
example: |
  println new com.concur.Http().addMapToQueryString('https://example.com/api', ['a': 'b', 'c': 'd'])
  // https://example.com/api?a=b&c=d
 */
def addMapToQueryString(String uri, Map data) {
  data.each {
    uri = addToUriQueryString(uri, it.key, it.value)
  }
  return uri
}

/*
description: Send a slack message. Prior to sending a message there is a check to see if the Slack plugin is installed. Can see more about the parameters for slackSend from [Slack Plugin](https://github.com/jenkinsci/slack-plugin).
note: This does not currently do anything if the plugin is installed.
example: |
  new com.concur.Http().sendSlackMessage(['channel': 'notifications', 'tokenCredentialId': 'f7136118-359a-4fcb-aba8-a1c6ee7ecb9b', 'message': 'example slack message'])
 */
def sendSlackMessage(Map slackData=[:]) {
  new com.concur.Commands().debugPrint(['slackData': slackData], 2)
  def slackPluginInstalled = new Commands().getPluginVersion('slack')
  new Commands().debugPrint(slackData)
  if (slackPluginInstalled) {
    slackSend(slackData)
  } else {
    println "Slack plugin not installed"
  }
}
