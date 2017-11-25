#!/usr/bin/env groovy
package com.concur

import groovy.transform.Field;
import jenkins.model.*;
import org.yaml.snakeyaml.*;
import com.cloudbees.hudson.plugins.folder.*;
import com.cloudbees.hudson.plugins.folder.properties.*;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider.FolderCredentialsProperty;
import com.cloudbees.plugins.credentials.impl.*;
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.*;
import org.codehaus.groovy.runtime.GStringImpl;

@Field def concurUtil = new com.concur.Util()

// ########################
// Workflow Execution Methods
// ########################

// Run the workflow steps for the appropriate sections
def runSteps(yml, branch=env.BRANCH_NAME) {
  assert yml : """|No YML contents provided. An example pipelines.yml file would look like this:
                  |------------------------------------------------------------------------------
                  |pipelines:
                  |  tools:
                  |    branches:
                  |      feature: .+
                  |  branches:
                  |    feature:
                  |      steps:
                  |        - script: 'ls -la'""".stripMargin()
  branch = checkBranch(yml,branch)
  assert branch : """|Unable to determine branch pattern for ${env.BRANCH_NAME}
                     |----------------------------------------
                     |Please ensure your yml file contains a section under tools.branches.patterns
                     |pipelines:
                     |  tools:
                     |    branches:
                     |      patterns:
                     |        master: master
                     |        develop: develop
                     |        feature: .+
                     |        """.stripMargin()

  debugPrint("WorkflowLibs :: Commands :: runSteps", [
    "branch": branch,
    "yml": yml,
  ])

  def workflows = yml.branches?."$branch"?.steps

  assert workflows : """|Unable to determine steps to take for branch: ${branch}
                        |----------------------------------------
                        |Please check your YML structure to ensure it is correct, example:
                        |branches:
                        |  feature:
                        |    steps:
                        |      - slack:
                        |        - send: "The {{ current_branch }} branch has a pull request available."
                        """.stripMargin()
  def workflowsList = []
  try{
    workflowsList = workflows.toList()
  } catch (e) {
    error("""|Error while trying to execute workflow steps
             |Please ensure your steps are formatted correctly.
             |A '-' at the beginning of a line indicates a list item which your steps should be
             |----------------------------------------
             |Correct:
             |branches:
             |  master:
             |    steps:
             |      - script: "echo 'Hello World'"
             |
             |Incorrect
             |branches:
             |  master:
             |    steps:
             |      script: "echo 'Hello World'"
             """.stripMargin())
  }
  try {
    workflowsList.each { workflow ->
      executeWorkflow(workflowsList, yml)
    }
  } catch(e) {
    currentBuild.result = 'FAILED'
    throw e
  }
}

private executeWorkflow(workflow, yml) {
  def stages = []
  def stageNum = 0
  workflow.each { section ->
    def workflowName = section.key
    def workflowFile = loadWorkflows("${workflowName}", yml)
    section.value.each { step ->
      def stepName = step instanceof Map ? step.keySet().first() : step
      def stageName = "${workflowName}: ${stepName}"
      def existingStageNames = stages.findAll{ it == stageName }
      if (existingStageNames.size() > 0) {
        stages.add(stageName)
        stageName = "${stageName} (${existingStageNames.size()+1})"
      } else {
        stages.add(stageName)
      }
      def stageStart = System.currentTimeMillis()
      try {
        stage(stageName) {
          if (step instanceof String || step instanceof org.codehaus.groovy.runtime.GStringImpl) {
            debugPrint('WorkflowLibs :: Commands :: executeWorkflows :: basic step', [
              'workflowName': workflowName,
              'stepName': step
            ])
            workflowFile."${step}"()
          } else if (step instanceof Map) {
            def params = step[stepName]
            debugPrint('WorkflowLibs :: Commands :: executeWorkflows :: parameterized step', [
              'workflowName': workflowName,
              'stepName': stepName,
              'params': params
            ])
            executeParameterizedStep(workflowFile, workflowName, stepName, params, yml)
          }
        }
      } catch(e) {
        currentBuild.result = 'FAILED'
        throw e
      }
    }
  }
}

private executeParameterizedStep(workflow, sectionName, stepName, stepValues, yml) {
  debugPrint('WorkflowLibs :: Commands :: executeParameterizedStep :: parameters', [
    'workflow'    :workflow,
    'sectionName' : sectionName,
    'stepName'    :stepName,
    'stepValues'  :stepValues,
    'yml'         :yml
  ])
  Boolean doubleMap = workflow.metaClass.respondsTo(workflow, stepName, Map, Map)
  Boolean singleMap = workflow.metaClass.respondsTo(workflow, stepName, Map)
  
  if (doubleMap) {
    debugPrint('WorkflowLibs :: Commands :: executeParameterizedStep',
      "Executing ${stepName} with yml and args".center(80, '-'))
    workflow."${stepName}"(yml, stepValues)
  } else if (singleMap) {
    debugPrint('WorkflowLibs :: Commands :: executeParameterizedStep',
      "Executing ${stepName} with only yml".center(80, '-'))
    workflow."${stepName}"(yml)
  } else {
    error("""|Error while trying to execute workflow step: ${stepName}
             |Please ensure the ${stepName} exists in the ${sectionName}.groovy file as a public method.
             |------------------------------------------------------------------------------------------
             |When using a custom workflow please ensure the styles match.
             |Example: WorkflowLibs 3.x with parameters need to be formatted like this:
             |--------------------------------------------------------
             |custom.groovy
             |
             |public method(yml,args) {
             |}
             |--------------------------------------------------------
             |With a pipelines.yml file similar to this:
             |
             |branches:
             |  master:
             |    steps:
             |      - custom:
             |          arg1: var1
             |--------------------------------------------------------
             |Custom workflows without parameters will be formatted like this:
             |--------------------------------------------------------
             |custom.groovy
             |
             |public method() {
             |}
             |--------------------------------------------------------
             |With a pipelines.yml file similar to this:
             |
             |branches:
             |  master:
             |    steps:
             |      - custom
             |--------------------------------------------------------
             """.stripMargin())
  }
}

private getStageName(workflow) {

}

// Workflow loader
private loadWorkflows(fileName, yml) {
  def repo                = yml.tools?.jenkins?.workflows?.repo         ?: env.WORKFLOW_REPOSITORY
  def branch              = yml.tools?.jenkins?.workflows?.branch       ?: yml.tools?.jenkins?.workflows?.tag ?: 'master'
  def credentialCriteria  = yml.tools?.jenkins?.workflows?.credentials  ?: ['description': env.WORKFLOW_GIT_CREDENTIAL_DESCRIPTION]
  def workflowDir         = yml.tools?.jenkins?.workflows?.directory    ?: 'workflows'
  def nodeLabel           = yml.tools?.jenkins?.workflows?.label        ?: 'linux'

  assert fileName : "fileName field has an invalid value."

  debugPrint('WorkflowLibs :: Commands :: loadWorkflows', ['fileName' : fileName, 'repo' : repo, 'branch' : branch, 'credentialCriteria' : credentialCriteria])

  fileName = "${fileName}.groovy"
  def localFile = "${workflowDir}/${fileName}"
  def localFileExists = fileExists "${localFile}"
  debugPrint('WorkflowLibs :: Commands :: loadWorkflows', ['localFile' : localFile,'localFileExists' : localFileExists])

  def workflow

  if (localFileExists) {
    workflow = load localFile
  } else {
    assert repo : "Repo to checkout for workflows not set under tools.jenkins.workflows.repo or as the environment variable: WORKFLOW_REPOSITORY."
    // only search for credential if needed
    def credentialsId = getCredentialsWithCriteria(credentialCriteria).id
    assert credentialsId
    debugPrint('WorkflowLibs :: Commands :: loadWorkflows :: credentials', credentialsId)
    workflow = fileLoader.fromGit(fileName,repo, branch, credentialsId, nodeLabel)
  }
  assert workflow : "Workflow file ${fileName} not found or unable to load from remote repo."

  return workflow
}

// Check branch pattern
def checkBranch(yml, branch=env.BRANCH_NAME) {
  assert yml    : "Couldn't find pipelines.yml."
  assert branch : "Branch name not set. This should typically be set by the environment as BRANCH_NAME. Please ensure this is being called within a node."

  def patterns = yml.tools?.branches?.patterns
  assert patterns : """|Define your branch patterns under tools.branches.patterns
                       |---------------------------------------------------------
                       |Example YML
                       |pipelines:
                       |  tools:
                       |    branches:
                       |      patterns:
                       |        feature: .+""".stripMargin()

  def branchType = patterns.find {
    branch.matches(it.value)
  }.key

  debugPrint("WorkflowLibs :: Commands :: checkBranch", [
    'branch'    : branch,
    'patterns'  : patterns,
    'branchType': branchType
  ])

  return branchType
}

// ########################
// # Jenkins Credentials
// ########################

/*
* Get the credentials based on criteria defined in a map
*
* Map parameters:
*
*   @param class -  enum CredentialTypes of what kind of credential to search for
*   @param id - jenkins id of the credential
*   @param password - password of the credential
*   @param description - description of the credential
*
* Other parameters can be passed and will be evaluated if they are properties of the credential type passed in the map
*/
def getCredentialsWithCriteria(criteria) {

  debugPrint("WorkflowLibs :: Commands :: getCredentialsWithCriteria", criteria)

  // Make sure properties isn't empty
  assert criteria : "No criteria provided."

  if (criteria.keySet().contains('class')) {
    assert criteria."class".class != java.lang.String : "java.lang.String is not a valid class for credentials"
    criteria."class" = criteria."class".class == CredentialTypes ? criteria."class"?.getValue() : criteria."class"
    assert criteria."class" in CredentialTypes.getValues() : "Credential type ${criteria.'class'} is not supported or is invalid."
  }

  // Number of properties that that are in the map
  def count = criteria.keySet().size()
  def credentials = []

  // Get all of the global credentials
  def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
    com.cloudbees.plugins.credentials.impl.BaseStandardCredentials.class,
    jenkins.model.Jenkins.instance)
  // Get credentials for the folder that the job is in
  java.util.ArrayList folderCreds = new java.util.ArrayList()
  def folderNames = env.JOB_NAME ?: ""
  def folders = folderNames.split('/')
  for (int i = 0; i < folders.size(); i++) {
    def folderName = folders[0..i].join('/')
    try {
      for(n in getFolderCredentials(folderName)) {
        folderCreds << n
        debugPrint("WorkflowLibs :: Commands :: getCredentialsWithCriteria", folderCreds)
      }
    } catch (Exception e) {
      debugPrint("WorkflowLibs :: Commands :: getCredentialsWithCriteria",
      "Unable to get credentials for folder: ${folderName}")
    }
  }
  // Separately loop through credentials provided by different credential providers
  for(s in [folderCreds, creds]) {
    // Filter the results based on description and class
    for (c in s) {
      def i = 0
      if(count == c.getProperties().keySet().intersect(criteria.keySet()).size()) {
        if(c.getProperties().keySet().intersect(criteria.keySet()).equals(criteria.keySet())) {
          for ( p in c.getProperties().keySet().intersect(criteria.keySet())) {
            if (c."${p}" != criteria."${p}") {
              break;
            } else {
              i++;
            }
          }
        }
      }
      if (i == count) {
        credentials << c
      }
    }
  }
  // Fail if no credentials are found that match the criteria
  assert credentials : """No credentials found that match your criteria: ${criteria}"""
  assert credentials.size() == 1 :  """
  ${
    println "Multiple credentials found for search criteria.\n"
    println "Criteria:"
    println criteria
    println ""
    println "Credentials:"
    for(l in credentials) {
      println "id: ${l.id} description: ${l.description}"
    }
  }
  """
  // Get the single credential
  def credential = credentials[0]
  assert credential.id : "Invalid credentials. The id property of your credential is blank or corrupted."
  // Return the credentials
  return credential
}

// Get credentials for a given folder name
private getFolderCredentials(folderName) {
  debugPrint("WorkflowLibs :: Commands :: getFolderCredentials :: Getting credentials for folder", folderName)
  def folder = Jenkins.instance.getItemByFullName(folderName)

  AbstractFolder<?> folderAbs = AbstractFolder.class.cast(folder)
  FolderCredentialsProperty property = folderAbs.getProperties().get(FolderCredentialsProperty.class)
  return property.getCredentials()
}

// Convert to a serializable list
@NonCPS
def jenkinsMap(gmap){
  def safeList = []
  gmap.each {
    safeList.add(new java.util.AbstractMap.SimpleImmutableEntry(it.key, it.value))
  }
  safeList
}

// Enum for Credentials
enum CredentialTypes {

    usernamePassword (com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl),
    sshPrivateKey (com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey),
    stringCredentials (org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl),
    fileCredentials (org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl)

    final Class cValue

    CredentialTypes(Class value) {
      this.cValue = value
    }

    Class getValue() {
      return this.cValue
    }

    String getKey() {
      name()
    }

    static List getValues() {
      List l = []
      this.values().each {
        l << it.getValue()
      }
      return l
    }
}

// ########################
// # Helpers
// ########################

// get a version number for the provided plugin name
def getPluginVersion(pluginShortName) {
  assert pluginShortName : "Plugin name must be provided."
  return Jenkins.getInstance().pluginManager.getPlugin("${pluginShortName}")?.getVersion()
}

def getPipelineDataFile(fileName = 'pipelines.yml', format = 'yml', baseNode = 'pipelines') {
  def fileContents = readFile fileName
  def dataMap = null
  switch(format.toLowerCase()) {
    case "yml":
    case "yaml":
      dataMap = concurUtil.parseYAML(fileContents)
      break;
    case "json":
      dataMap = concurUtil.parseJSON(fileContents)
      break;
  }
  if (dataMap?."${baseNode}"?.general?.debug) {
    env.DebugMode = true // this being set allows for a lot of debugging output to be included.
  }
  return dataMap[baseNode]
}

// check the environment to see if we are in debug mode
def isDebug() {
  return env.DebugMode?.toBoolean() ?: false
}

/* usage examples
  new com.concur.Commands().debugPrint('workflows :: docker', 'building docker image ...')
  new com.concur.Commands().debugPrint('workflows :: docker', ['docker image name : ${dockerImageName}'])
  new com.concur.Commands().debugPrint('workflows :: docker', ['docker image name': dockerImageName])
 */
def debugPrint(title, msgdata, debugMode=null) {
  if (debugMode == null) {
    debugMode = isDebug()
  }
  if (debugMode) {
    println "### \u001B[35mDebug output for $title\u001B[0m ###"
    if (msgdata instanceof Map) {
      msgdata.each { data ->
        println "### \u001B[35mDebug >>> ${data.key}: ${data.value}\u001B[0m"
      }
    } else if (msgdata instanceof List) {
      msgdata.each { msg ->
        println "### \u001B[35mDebug >>> ${data}\u001B[0m"
      }
    } else {
      println "### \u001B[35mDebug >>> ${msgdata}\u001B[0m"
    }
    println "### \u001B[35mEnd Debug\u001B[0m ###"
  }
}
