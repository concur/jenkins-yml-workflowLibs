#!/usr/bin/groovy
// vars/plGithubCheckout.groovy
// evaluate the body block, and collect configuration into the object
import com.concur.*;

def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  // Gather the configuration variables
  repo            = config.repo         ?: null
  credentials     = config.credentials  ?: null
  withSubmodules  = config.withSubmodules == null ? false : config.withSubmodules

  // Get any untility commands that we need
  def concurPipeline  = new Commands()
  def gitPipeline     = new Git()

  concurPipeline.debugPrint([
    'repo'          : config.repo,
    'credentials'   : config.credentials,
    'withSubmodules': config.withSubmodules
  ])

  def scmVars = [:]

  // Get the project
  if (repo != null && credentials != null) {
    concurPipeline.debugPrint("Checking out ${repo}")
    git credentialsId: credentials, url: repo
  } else if (withSubmodules) {
    concurPipeline.debugPrint('Checking out scm with submodules')
    scmVars = checkout changelog: false,
        scm: [$class: 'GitSCM',
              branches: scm.branches,
              doGenerateSubmoduleConfigurations: false,
              extensions: [
                [$class: 'SubmoduleOption',
                  disableSubmodules: false,
                  parentCredentials: true,
                  recursiveSubmodules: true,
                  reference: '',
                  trackingSubmodules: false]
              ],
              submoduleCfg: scm.submoduleCfg,
              userRemoteConfigs: scm.userRemoteConfigs
        ]
  } else {
    concurPipeline.debugPrint('Basic checkout from GitHub')
    scmVars = checkout scm
  }
  gitPipeline.saveGitProperties(scmVars)
}
