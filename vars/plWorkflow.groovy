#!/usr/bin/env groovy

import com.concur.*;

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def concurPipeline  = new Commands()

  // variables from closure
  def nodeType              = config.nodeType       ?: 'linux'
  def pipelineDataFilePath  = config.yamlPath       ?: 'pipelines.yml'

  def slackNotify           = config.notify             == null ? true : config.notify
  def gitSubmodules         = config.useSubmodules      == null ? true : config.useSubmodules
  def timeoutDurationInt    = config.timeoutDuration
  def timeoutUnitStr        = config.timeoutUnit

  // local script variables
  def timedNode = nodeType.toLowerCase().equals('linux') ? plLinux : plWindows

  timedNode timeoutDurationInt, timeoutUnitStr, {
    stage ('git: checkout') {
      plGitCheckout {
        withSubmodules = gitSubmodules
      }
    }

    println "Loading pipeline data file."
    def yml = concurPipeline.getPipelineDataFile(pipelineDataFilePath)
    concurPipeline.debugPrint([
      'nodeType'        : nodeType,
      'yamlPath'        : pipelineDataFilePath,
      'notify'          : slackNotify,
      'useSubmodules'   : gitSubmodules,
      'timeoutDuration' : timeoutDurationInt,
      'timeoutUnit'     : timeoutUnitStr
    ])

    if (yml.general?.dateFormat) {
      env."${Constants.Env.DATE_FORMAT}" = yml.general.dateFormat
    }

    // pulling out slack map to add values to it in the event that the token doesn't exist
    def slackData = yml?.tools?.slack

    if (slackNotify) {
      println "Sending Slack notification for build start..."
      assert slackData?.channel
      if (!slackData?.token) {
        plNotify('STARTED', (slackData?.useAttachments ?: true), slackData?.channel)
      } else {
        plNotify('STARTED', (slackData?.useAttachments ?: true), slackData?.channel, slackData?.token)
      }
    }

    try {
      // now build, based on the configuration provided
      println "Executing pipeline steps"
      concurPipeline.runSteps(yml)

      currentBuild.result = 'SUCCESS'
    } catch (e) {
      currentBuild.result = 'FAILURE'
      throw e
    } finally {
      currentBuild.result = currentBuild.result ?: 'FAILURE'
      if (slackNotify) {
        if (!slackData?.token) {
          plNotify(currentBuild.result, (slackData?.useAttachments ?: true), slackData?.channel)
        } else {
          plNotify(currentBuild.result, (slackData?.useAttachments ?: true), slackData?.channel, slackData?.token)
        }
      }
    }
  }
}
