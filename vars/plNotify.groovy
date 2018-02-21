#!/usr/bin/groovy
// vars/bhNotify.groovy
import groovy.json.internal.Exceptions;
import com.concur.*;

// Send Slack notifications to the interested channel
// This is uses the configuration from the plugin on the Jenkins master if nothing is provided
def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  Commands concurPipeline = new Commands()
  Git concurGit           = new Git()
  Http concurHttp         = new Http()

  String buildStatus    = config?.buildStatus     ?: currentBuild.result      ?: 'SUCCESS'
  String domain         = config?.domain          ?: env.DEFAULT_SLACK_DOMAIN
  String host           = config?.host            ?: env.GIT_HOST
  String org            = config?.org             ?: env.GIT_OWNER
  String repo           = config?.repo            ?: env.GIT_REPO

  String useAttachments = config?.useAttachments == null ? true : config?.useAttachments
  String channel        = config?.channel
  String token          = config?.token

  // No need to proceed if there is no org or repo
  if( !org || !repo) {
    println "${Constants.Colors.RED}Not able to send a slack notification. No org or repo defined.${Constants.Colors.CLEAR}"
    return
  }

  // Default values for parameters
  def attachments = []
  List fields = [
    [
      'title': 'Job',
      'value': "${env.JOB_NAME.replaceAll('%2F', '/')}",
      'short': false
    ],
    [
      'title': 'Branch',
      'value': "${env.BRANCH_NAME}",
      'short': true
    ],
    [
      'title': 'Datacenter',
      'value': "${env.JENKINS_DATACENTER}",
      'short': true
    ],
    [
      'title': 'Build',
      'value': "<${env.BUILD_URL}|${env.BUILD_NUMBER}>",
      'short': true
    ],
    [
      'title': 'Author',
      'value': "${env.GIT_AUTHOR}",
      'short': true
    ],
    [
      'title': 'Commit',
      'value': "<http://github.concur.com/${org}/${repo}/commit/${env.GIT_SHORT_COMMIT}|${env.GIT_SHORT_COMMIT}>".stripMargin(),
      'short': true
    ],
  ]

  // Override default values based on build status
  if (buildStatus == 'STARTED') {
    color     = 'BLUE'
    colorCode = '#87CEEB'
    attachments.add([
      'text': "*Build Number* <${env.BUILD_URL}|${env.BUILD_NUMBER}> started on *${env.JENKINS_URL.replaceAll('/$', "")}* for branch *${env.BRANCH_NAME}*",
      'color': colorCode
    ])
  } else if (buildStatus == 'SUCCESS') {
    color     = 'GREEN'
    colorCode = '#3CB371'
    attachments.add([
      'color' : colorCode,
      'text'  : 'Build complete',
      'fields': fields
    ])
  } else {
    color     = 'RED'
    colorCode = '#DC143C'
    attachments.add([
      'color' : colorCode,
      'text'  : 'Build failed',
      'fields': fields
    ])
  }

  // Send notifications
  try {
    if (channel != null && token != null && domain != null) {
      concurSlack.send(message: buildStatus, attachments: attachments, token: token, teamDomain: domain, channel: channel)
    } else if (channel != null && domain != null) {
      def cred = concurPipeline.getCredentialsWithCriteria(['description': 'Slack Integration Token']).id
      concurSlack.send(message: buildStatus, attachments: attachments, tokenCredentialId: cred, teamDomain: domain, channel: channel)
    } else {
      // NOTE: without a channel set, it will send using default channel for default token.
      concurSlack.send(message: buildStatus, attachments: attachments)
    }
  } catch (java.lang.NoSuchMethodError | Exception e) {
    println "WorkflowLibs :: bhNotify :: Not able to send slack notification. Please make sure that the plugin is installed and configured correctly."
  }
}
