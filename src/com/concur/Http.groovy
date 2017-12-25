#!/usr/bin/env groovy
package com.concur

@NonCPS
def addRequestHeader(String k, String v, String map) {
  map.add(['name': k, 'value': v])
}

def addToUriQueryString(String uri, String k, String v) {
  String key = java.net.URLEncoder.encode(k, "UTF-8")
  String value = java.net.URLEncoder.encode(v, "UTF-8")
  return uri.contains('?') ? "${uri}&${key}=${value}" : "${uri}?${key}=${value}"
}

def sendSlackMessage(Map slackData=[:]) {
  new com.concur.Commands().debugPrint(['slackData': slackData], 2)
  def slackPluginInstalled = new Commands().getPluginVersion('slack')
  new Commands().debugPrint(slackData)
  if (slackPluginInstalled) {
    slackSend(slackData)
  }
}
