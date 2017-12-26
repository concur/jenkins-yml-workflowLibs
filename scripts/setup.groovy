#!/usr/bin/env groovy

/*
 * This script will setup the global library as well as set some env vars and add an ssh deploy key credential so that the workflows can be checked out.
 * To execute as an admin go to your Jenkins box and go to the script console. Copy/paste the script as is into the script console and it should be set up after running.
 */

// GLOBAL VARS
instance = jenkins.model.Jenkins.getInstance()

sshKeyDescription = 'WorkflowLibs SSH Credential'
sshDeployKeyContents = '''-----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEAvDBxEA6c7Ph03CVbFvUTcfj+hbKUf7eEXXHHO40iYCKn23FF
OX5s0Aw5ylHxuroGUNtwDI80tZfFH0e4Ose2wJyG9A4bCo8MRFsMlSs9BInHsV5H
la4cUQ+sK8+WyWe1AqpiKNQngSLrgShQd8tyM2dW7nppcW0W3kwh/Fm0Ah17jrh1
IKm5cZVm4L5H59LHYdx+0w6xyG5v8md4G/B1mT23G/FETQOs/+Rl7tUsMJHLRktY
Tu8/TxAhISzacI6Jew02LzT1d5YVrLm2Lf66yKguVLLfvbaOA9lTVxpmze/hbN0Z
vX3OsTObARhITKPALrlrw8hs4VIgQ5fPE0Ak5wIDAQABAoIBAHVaL+c3dZxAg38U
vGzgfzO/ddihuAC8zAoJfZghNqKEefBZ/jUQJYLHXPJRj1BF2NJrRMBQPSpQblhH
PRdKmygZc/6VHT/EXH3z2TDcObyuvpxYkoNAg9/4ygC3/uuxhCsZXQTg55Gb2Qg9
v5A0ig6MDqXAKCjXQj91cmwO9DWsYwss7qXG9vPk5Kh9SfHVapiHgLcTtPJCboof
pMrNDveHrZ9yF0f6fW2LTANpVhf4j1Z7G8HhSAGFI57scINo7BLPkx9W4be0PAKZ
dwzUOEi4RZiVdNWybnEzaz6W/Iu5rlieL33D5WelxtoHdcp++LdvC/SF+9P4aiiq
dCWgF2ECgYEA4zOXMxxumSNk7T1S96l/YdzNHRy3Fh1/d5QUBIbJbvnq7x2NjFhd
SG+OwUZWqFlW1gJkG8vHBJB0T6TzLE0JrxnfnjNPVMTOOaOLbBDUNn4hWPAHSnMh
s1xN2MSqPhW8y9aeZM5CmKFysUN3ao43pl4se8l55GLFYM2KjCoHXo0CgYEA1Arz
MWmMZ3jvs4J7qPK8J3feCb+irTHdjRLxab7IzZ2aXBtbO4Nibm+Ob59D2Uytcc0L
x4seWHGaghtIPc/46r422jbqwfT3l/CxDEK64lJKaIsczbkvOZhS8vRxvjaenObF
wmpC7wej9Reqif5WB/wMum9oXS9SAK0FfSQtfkMCgYEAnSbrN6Q779rmjZ50S/BG
ttc2XSOmlDp1F64qJnNg0000ro5/gQsCAv9++7r+Z9Xb1Y2z1vIdx89vVEOhHAxx
XVeFMHUHM3gpuP6him8dik+2G0FzmZCHR/vZsM1fgDAi9c+OoeV1dQN/RJOI/wA+
B+pt1HWoxUt4gRvK8GaF0GUCgYEAonm1eRJAVHTVbv43lPBR/ggaKeweUYmZEuUF
+JoIsYzm31bS5Fo7DRYL1Tn7OkFH0aBlO0Q9P1XlJ3aSN1Lpj62qyDA2V+JF6bWz
ZAqpuouFmm+l5XjVV9OBE8r8cNzXUrB9rgfQO+nl1wKIWW56K2oVsrF1DZoZwozR
XN9gwJMCgYAQn/mnEap8L8+1AfoUrNIlPJWtoBaTTYQbKA9UCjNB6CoPLx2/qmfO
b2BLpbI42G72HPm0EPHU4wc7LkGTQP0FunStIA0TGIesdzTeiOFJVXvw2TCQ3IoX
BbiXoFBh78ybsANMfjjXNgsFzxX+naoAZRa28L7e+3uA1xFyQP3bRQ==
-----END RSA PRIVATE KEY-----
'''

jenkinsEnvVariables = [
  'WORKFLOW_GIT_CREDENTIAL_DESCRIPTION' : sshKeyDescription,
  'WORKFLOW_REPOSITORY'                 : 'git@github.com:reynn/jenkins-workflows.git',
  'DEFAULT_SLACK_TOKEN_DESC'            : 'Slack Token',
  'DEFAULT_SLACK_DOMAIN'                : '',
]

workflowLibsGitUrl        = 'git@github.com:reynn/jenkins-yml-workflowLibs.git'
workflowLibsName          = 'plWorkflowLibs'
workflowLibsDefaultBranch = 'master'

jenkinsRequiredScriptApprovals = [
  'method com.cloudbees.plugins.credentials.common.IdCredentials getId',
  'method java.lang.Class isInstance java.lang.Object'
]

// ******************************
// Add global cred
// ******************************

def jenkinsCreds = getJenkinsCredentials()
def credExists = jenkinsCreds.find { it.description == sshKeyDescription }
if (!credExists) {
  centerPrint("Adding credential with description: $sshKeyDescription")
  def cred = createSshCredentials(null, 'reynn', sshDeployKeyContents, null, sshKeyDescription)
  Jenkins.instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore().addCredentials(
    com.cloudbees.plugins.credentials.domains.Domain.global(),
    cred
  )
} else {
  centerPrint("Credential with description [$sshKeyDescription] already exists.", '#')
}

// ******************************
// Add env vars
// ******************************

def globalNodeProperties    = instance.getGlobalNodeProperties()
def envVarsNodePropertyList = globalNodeProperties.getAll(hudson.slaves.EnvironmentVariablesNodeProperty.class)

def envVars = null

if ( envVarsNodePropertyList == null || envVarsNodePropertyList.size() == 0 ) {
  def newEnvVarsNodeProperty = new hudson.slaves.EnvironmentVariablesNodeProperty();
  globalNodeProperties.add(newEnvVarsNodeProperty)
  envVars = newEnvVarsNodeProperty.getEnvVars()
} else {
  envVars = envVarsNodePropertyList.get(0).getEnvVars()
}

jenkinsEnvVariables.each { prop ->
  if (prop.value instanceof Map) {
    prop.value = groovy.json.JsonOutput.toJson(prop.value)
  }
  centerPrint("Adding ${prop.key.toUpperCase()} with value ${prop.value}".toString())
  envVars.put(prop.key.toUpperCase(), prop.value)
}

// ******************************
// Add global libraries
// ******************************

def existingCredentialId = getJenkinsCredentials().find { it.description == sshKeyDescription }?.id
assert existingCredentialId : "Failed to find a credential with description [$sshKeyDescription], please make sure it exists"
List workflowLibraries = new org.jenkinsci.plugins.workflow.libs.GlobalLibraries().libraries.collect { it }
if (!workflowLibraries.find { it.name == workflowLibsName }) {
  def scmConfig = new jenkins.plugins.git.GitSCMSource(
    java.util.UUID.randomUUID().toString(),
    workflowLibsGitUrl, // remote
    existingCredentialId, // credentialsId
    null, // remoteName
    null, // rawRefSpecs
    null, // includes
    null, // excludes
    false // ignoreOnPushNotifications
  )
  def libraryConfig = new org.jenkinsci.plugins.workflow.libs.LibraryConfiguration(workflowLibsName, new org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever(scmConfig))
  libraryConfig.setDefaultVersion(workflowLibsDefaultBranch)
  workflowLibraries.add(libraryConfig)
  new org.jenkinsci.plugins.workflow.libs.GlobalLibraries().setLibraries(workflowLibraries)
  centerPrint("Added $workflowLibsName to list of global libraries. The following libraries exist currently ${workflowLibraries.collect{ it.name }.join(',')}")
} else {
  centerPrint("WorkflowLibs with name $workflowLibsName already exists", '#')
}

// ******************************
// Add script approvals
// ******************************

def scriptApprovals = org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval.get()
jenkinsRequiredScriptApprovals.each {
  centerPrint("Adding ${it} to approved list")
  scriptApprovals.approveSignature(it)
}
centerPrint("Script approvals set")

// ******************************
// Ensure everything saves
// ******************************

instance.save()

// ******************************
// HELPERS
// ******************************

def getJenkinsCredentials() {
  return com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
    com.cloudbees.plugins.credentials.impl.BaseStandardCredentials.class,
    jenkins.model.Jenkins.instance)
}

def createSshCredentials(id, username, sshKey, sshPassphrase, description) {
  return new com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey(
    com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL,
    id,
    username,
    new com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(sshKey),
    sshPassphrase,
    description
  )
}

def centerPrint(text, sep='*', length=120) {
    println " ${text} ".center(length, sep)
}
