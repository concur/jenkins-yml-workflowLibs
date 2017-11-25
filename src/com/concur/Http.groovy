#!/usr/bin/env groovy
package com.concur

@NonCPS
def addRequestHeader(k, v, map) {
  map.add(['name': k, 'value': v])
}

def addToUriQueryString(uri, k, v) {
  String key = java.net.URLEncoder.encode(k, "UTF-8")
  String value = java.net.URLEncoder.encode(v, "UTF-8")
  return uri.contains('?') ? "${uri}&${key}=${value}" : "${uri}?${key}=${value}"
}

def sendSlackMessage(slackData=[:]) {
  new com.concur.Commands().debugPrint('workflowLibs :: sendSlackMessage', [
    'slackData'       : slackData,
    'slackData.class' : slackData.getClass()
  ])
  def slackPluginInstalled = new com.concur.Commands().getPluginVersion('slack')
  if (slackPluginInstalled) {
    slackSend(slackData)
  }
}
