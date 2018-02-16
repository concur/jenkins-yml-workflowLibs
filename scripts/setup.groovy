#!/usr/bin/env groovy

/*
 * This script will setup the global library as well as set some env vars and add an ssh deploy key credential so that the workflows can be checked out.
 * To execute as an admin go to your Jenkins box and go to the script console. Copy/paste the script as is into the script console and it should be set up after running.
 */

// GLOBAL VARS
instance = jenkins.model.Jenkins.getInstance()

workflowLibsSSHKeyDescription = 'WorkflowLibs SSH Credential'
// This is a SSH deploy key providing read access to this only repository.
workflowLibsSSHDeployKeyContents = '''-----BEGIN RSA PRIVATE KEY-----
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
-----END RSA PRIVATE KEY-----'''

workflowsSSHKeyDescription = 'Workflows SSH Credential'
// This is a SSH deploy key providing read access to this only repository.
workflowsSSHDeployKeyContents = '''-----BEGIN RSA PRIVATE KEY-----
MIIEpQIBAAKCAQEAy0OhgH1NhFArJdDeD73KFjZwxpp7j3n5gtXpEr9RFd2xk7iG
D9JprdqfdWLPbYy62XAfUuZEhIKjYEGgD+4sbqEePAMgwDJtJ6Y7JOl0NujdSejI
PCwc9FOoek0f1xZSQnPYXkP/hsDNZ4cHDc61kI1EO7U8FPOFXlFZrRpVsp9tnp3T
6zY0op+kIpfyM0AzIoPkElaWVU135UnO/q0Ck8UzDDwpVwpegNamXqX+ewjPLZz9
TZ0WxR3+icfGTZWUi1qoPS5O1gFqNZbmBEhDzUhP6Wk/s7L7tXKWqpI/MPKh2EF6
r8F3s+6v1cw6xBG0st3a7G7bc8gRjjX8A++vaQIDAQABAoIBAQCfHGi+sNdOHIc6
Cd7aCaG4c5MiE+lm7X8gKJxS/YnWiPypescOeZIZ8kniVJ+0zHWzIa6TcQbvh2em
QJiv/6uuSdbl+TjY2mBRqjEf+tLq7KvUYDDl0U40/4uheN/UuXdY51/WonnSnPfs
82jVKRs9dSeVaZAHgnzC0QFWnIe60uYuK8JmM0+noSG6h0bKdP3pCFRkcW14Xx1p
aMA0oKZ6uGEEeFU8Mtu8Yfso2M6sLLXU5gNpnBn3AZ+ycqY5GCAQKis45L8XDGUC
vvDp6zsWh8TtCfIVfxrcnfHyz4KjLhHOSxAXds1NNfZwO2tTCsqp4ldtCv7ddqBX
TxykOWTRAoGBAO8/HrB7QrOeIoNdN5KE7OA5VC7w9vKD2syXMHk8sPsuVVC0ncIQ
0ipAORggQSl1j94O7ewCDmfkJaQHeBZexkNel+6ScNxThj8xLlbmrnOUhdj+Q1I1
+9I9CzLQAzDTqqxH3GTABFueu65rb8hELze9/yyoqrnXbZ3OVPEVksGdAoGBANl/
eAeT1PR4V2AteJlwOBX0vMRkMpqsFW3eX6DVL/S3Wy0irnK/vo3t5mjCLx7XQbX2
qJlIpUF2zn4bwwgGfcrIk72y3TmF7AKYpCnFcGPaCD6dljh3SYbHFGLOmaDFqWHS
3AJY9Okpxv7PRI027E4isH7Km6gWkyQr/RFSYrE9AoGAXs0jKwlcr/H8eRNJ/wwf
FOaCnisrn4NyAtnyAkhzVMTSV62KSakCrm1OcmntiDEmdfZyfq9959r2s12mTy70
3yMvjwCGKjgKnSWu2A6GmBQFSavPH1d21qMLufHFIebt3WCIS3/u+iMW+ZFm9PNX
xn3KDHc1V3iu3fYxoGpNAd0CgYEAgSyGkiGHqcZLRpDa/m46sTeQYSeNMnWfqIgY
zYGwIKxBV3Ywm3Ar8UlovbEOoUeA/FxJV/hgRZgVHarJU1vfm+8yZ8jyQLa8K/KS
FUjw7izRFrcrP9AA+C4GeoRvk5+xcKr2BeLlWhF44V8iPKhxAhryLeuRNOxraWFC
xOXkPZUCgYEAmYAYUIIkCbnp7FWb5WHVl1XdKpU9Dxy7q41uRJOAQp6yEVN/kqov
pMomLo5ZUBAuVqYloWnklwOPXdHSGCNaIu1+plxpnD2FIv7XTMcG7dxD0kyomInl
Cz1ft7LCkpu+3UuvgUvdVycIqKygTTYtofgIVg9M+MLkbwJg7bek7ss=
-----END RSA PRIVATE KEY-----'''

jenkinsEnvVariables = [
  'WORKFLOW_GIT_CREDENTIAL_DESCRIPTION' : workflowsSSHKeyDescription,
  'WORKFLOW_REPOSITORY'                 : 'git@github.com:concur/jenkins-workflows.git',
  'DEFAULT_SLACK_TOKEN_DESC'            : 'Slack Token',
  'DEFAULT_SLACK_DOMAIN'                : '',
]

workflowLibsGitUrl        = 'git@github.com:concur/jenkins-yml-workflowLibs.git'
workflowLibsName          = 'plWorkflowLibs'
workflowLibsDefaultBranch = 'master'

jenkinsRequiredScriptApprovals = [
  'method com.cloudbees.plugins.credentials.common.IdCredentials getId',
  'method java.lang.Class isInstance java.lang.Object',
  'staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods center java.lang.String java.lang.Number',
  'staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods first java.lang.Iterable',
  'staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods getAt java.util.List groovy.lang.Range',
  'staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods multiply java.lang.String java.lang.Number'
]

// ******************************
// Set the Markup formatter to Safe HTML
// ******************************

Jenkins.instance.setMarkupFormatter(new hudson.markup.RawHtmlMarkupFormatter(false))

// ******************************
// Add global cred
// ******************************

def jenkinsCreds = getJenkinsCredentials()
def credExists = jenkinsCreds.find { it.description == workflowLibsSSHKeyDescription }
if (!credExists) {
  centerPrint("Adding credential with description: $workflowLibsSSHKeyDescription")
  def cred = createSshCredentials(null, 'concur', workflowLibsSSHDeployKeyContents, null, workflowLibsSSHKeyDescription)
  Jenkins.instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore().addCredentials(
    com.cloudbees.plugins.credentials.domains.Domain.global(),
    cred
  )
} else {
  centerPrint("Credential with description [$workflowLibsSSHKeyDescription] already exists.", '#')
}

credExists = jenkinsCreds.find { it.description == workflowsSSHKeyDescription }
if (!credExists) {
  centerPrint("Adding credential with description: $workflowsSSHKeyDescription")
  def cred = createSshCredentials(null, 'concur', workflowsSSHDeployKeyContents, null, workflowsSSHKeyDescription)
  Jenkins.instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore().addCredentials(
    com.cloudbees.plugins.credentials.domains.Domain.global(),
    cred
  )
} else {
  centerPrint("Credential with description [$workflowsSSHKeyDescription] already exists.", '#')
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

def existingCredentialId = getJenkinsCredentials().find { it.description == workflowLibsSSHKeyDescription }?.id
assert existingCredentialId : "Failed to find a credential with description [$workflowLibsSSHKeyDescription], please make sure it exists"
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
