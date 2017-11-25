#!/usr/bin/groovy
// vars/bhNotify.groovy
import groovy.json.internal.Exceptions

// Send Slack notifications to the interested channel
// This is uses the configuration from the plugin on the Jenkins master if nothing is provided
def call(buildStatus, channel = '', token = '', domain = 'concur-blue', org = '', repo = '') {

  def concurGithub    = new com.concur.GitHubApi()
  def concurPipeline  = new com.concur.Commands()

  def orgAndRepo  = concurGithub.getGitHubOrgAndRepo()
  org             = org   ?: orgAndRepo.org
  repo            = repo  ?: orgAndRepo.repo

  // No need to proceed if there is no org or repo
  if( !org || !repo) {
    println "\u001B[31mNot able to send a slack notification. No org or repo defined.\\u001B[0m"
    return
  }

  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'
  channel     = channel     ?: null
  token       = token       ?: null
  domain      = domain      ?: null

  // Default values for parameters
  def colorName = 'RED'
  def colorCode = '#DC143C'
  def subject   = "${buildStatus}"
  def summary   = """|${subject}
                     |*Job:* ${env.JOB_NAME.replaceAll('%2F', '/')}
                     |*Branch:* ${env.BRANCH_NAME}
                     |*Build:* <${env.BUILD_URL}|${env.BUILD_NUMBER}>
                     |*Author:* ${env.GIT_AUTHOR}
                     |*Commit:* <http://github.concur.com/${org}/${repo}/commit/${env.GIT_SHORT_COMMIT}|${env.GIT_SHORT_COMMIT}>""".stripMargin()

  // Override default values based on build status
  if (buildStatus == 'STARTED') {
    color     = 'BLUE'
    colorCode = '#87CEEB'
    subject   = "${buildStatus}\n"
    summary   = "${subject}*Build Number* <${env.BUILD_URL}|${env.BUILD_NUMBER}> started on *${env.JENKINS_URL.replaceAll('/$', "")}* for branch *${env.BRANCH_NAME}*"
  } else if (buildStatus == 'SUCCESS') {
    color     = 'GREEN'
    colorCode = '#3CB371'
  } else {
    color     = 'RED'
    colorCode = '#DC143C'
  }

  // Send notifications
  try {
    if (channel != null && token != null && domain != null) {
      slackSend(color: colorCode, message: summary, token: token, teamDomain: domain, channel: channel)
    } else if (channel != null && domain != null) {
      def cred = concurPipeline.getCredentialsWithCriteria(['description': env.DEFAULT_SLACK_TOKEN_DESC]).id
      slackSend(color: colorCode, message: summary, tokenCredentialId: cred, teamDomain: domain, channel: channel)
    } else {
      // NOTE: without a channel set, it will send using default channel for default token.
      slackSend (color: colorCode, message: summary)
    }
  } catch (java.lang.NoSuchMethodError | Exception e) {
    println "Not able to send slack notification. Please make sure that the plugin is installed and configured correctly."
  }
}
