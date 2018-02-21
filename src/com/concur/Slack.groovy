package com.concur

import groovy.transform.Field;

@Field def concurPipeline = new Commands()
@Field def concurUtil     = new Util()

/*
description: Make a request to the Slack API, attempts are made to ensure the request will be valid.
example: |
  def concurSlack = new com.concur.Slack()
  def credential = new com.concur.ConcurCommands().getCredentialsWithCriteria(['description': 'example credential description'])
  def slackData = ['tokenCredentialId': tokenCredential.id,'channel': 'auto-workflow-libs','message': 'Hello from custom com.concur.Slack.send','color': 'good']
  concurSlack.send(slackData)
  // no output is shown in Jenkins but the message should show up in the specified channel.
 */
def send(Map slackData) {
  setSlackDefaults(slackData)
  Boolean debugMode = concurPipeline.isDebug() ?: false

  def responseCodes = '100:399'
  if (!(slackData.failOnError ?: false)) {
    // there are times with the GitHub API in particular where a 404 is acceptable result,
    // this will ensure the httpRequest plugin does not fail the build in for acceptable 404s
    responseCodes = '100:599'
  }
  def slackUrl = getSlackUrl(slackData)
  // Check to make sure no values are invalidly set to null, this will cause a failure that is hard to determine.
  assert !slackUrl.contains('null') : 'WorkflowLibs :: Slack :: Send :: There was an error determining the Slack URL to send data to. Result [${slackUrl}]'
  def postData = new com.concur.Util().toJSON(formatSlackPostData(slackData))
  concurPipeline.debugPrint(['postData': postData])

  def response = httpRequest(
    url                 : slackUrl,
    quiet               : !debugMode,
    httpMode            : 'POST',
    requestBody         : postData,
    validResponseCodes  : responseCodes,
    httpProxy           : env.HTTP_PROXY,
    timeout             : 10
  )
  concurPipeline.debugPrint(["Slack response": response.content])
}

private getSlackUrl(Map slackData) {
  String slackUrl = ""
  if (!slackData.isBotUser) {
    slackUrl = "https://${slackData.teamDomain}.slack.com/services/hooks/jenkins-ci?token=${slackData.token}"
  } else {
    slackUrl = "https://slack.com/api/chat.postMessage?token=${slackData.token}&channel=${slackData.channel}&link_names=1&as_user=true"
  }
  return slackUrl
}

private formatSlackPostData(Map slackData) {
  def postData = [
    'text'    : slackData.message,
    'color'   : slackData.color
  ]
  if (slackData.channel) {
    postData.put('channel', slackData.channel)
  }
  if (slackData.attachments) {
    assert (slackData.attachments instanceof List) : "WorkflowLibs :: Slack :: formatSlackPostData :: Attachments must be a list, see https://api.slack.com/docs/message-attachments for more information on how to use attachments."
    postData.attachments = formatSlackAttachments(slackData.attachments)
  }

  return postData
}

private formatSlackAttachments(List slackAttachments) {
  List attachments = []
  slackAttachments.each { attachment ->
    if (attachment.text && !attachment.fallback) {
      attachment.put('fallback', attachment.text)
    }
    assert (attachment.text && attachment.fallback) : "WorkflowLibs :: Slack :: formatSlackAttachments :: Attachments must have either a fallback and/or text fields, provided attachment: |${attachment}|"
    attachments.add(attachment)
  }
  return attachments
}

private setSlackDefaults(Map slackData) {
  if (slackData.isBotUser) {
    // fail fast if they haven't specified a channel
    assert slackData.channel : """WorkflowLibs :: Slack :: setSlackDefaults :: No channel provided to send to, please check provided values.
    |${slackData.collect { "${it.key}=${it.value}" }.join('\n')}
    """.stripMargin()
  }
  assert slackData.message : "WorkflowLibs :: Slack :: setSlackDefaults :: No message provided to send to the channel."
  // when sending to a channel it needs a # at the beginning, a user would need a @ instead.
  if (!slackData.channel?.startsWith('#') && !slackData.channel?.startsWith('@')) {
    slackData.channel = "#${slackData.channel}"
  }
  if (!slackData.teamDomain) {
    slackData.put('teamDomain', (env."${Constants.Env.SLACK_CHANNEL}" ?: 'concur-blue'))
  }
  if (!slackData.isBotUser) {
    slackData.put('isBotUser', false)
  }
  if (!slackData.failOnError) {
    slackData.put('failOnError', false)
  }
  // Load the credential if provided, otherwise load our default token.
  if (!slackData.token && !slackData.tokenCredentialId) {
    def slackCredential = concurPipeline.getCredentialsWithCriteria(['description': 'Slack Integration Token'])
    slackData.put('token', slackCredential.getSecret().getPlainText())
  } else if (!slackData.token && slackData.tokenCredentialId) {
    def slackCredential = concurPipeline.getCredentialsWithCriteria(['id': slackData.tokenCredentialId])
    slackData.put('token', slackCredential.getSecret().getPlainText())
  }
}
