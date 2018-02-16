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

@Field def concurUtil = new Util()

// ########################
// Workflow Execution Methods
// ########################

/*
description: Run the workflow steps for the appropriate sections
examples:
  - |
    def concurCommands = new com.concur.Commands()
    def yaml = readYaml 'pipelines.yml'
    concurCommands.runSteps(yaml.pipelines)
 */
def runSteps(Map yml, String branch=env.BRANCH_NAME) {
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

  debugPrint(["branch": branch, "yml": yml,])

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
  workflowsList.each { workflow ->
    executeWorkflow(workflow, yml)
  }
}

private executeWorkflow(Map workflow, Map yml) {
  List stages = []
  int stageNum = 0
  workflow.each { section ->
    String workflowName = section.key
    def workflowFile = loadWorkflows(workflowName, yml)
    section.value.each { step ->
      def stepName = step instanceof Map ? step.keySet().first() : step
      long stageStart = System.currentTimeMillis()
      String stageName = ""
      try {
        def params = step[stepName]
        debugPrint([
          'workflowName': workflowName,
          'stepName': stepName,
          'params': params
        ])
        stageName = getStageName(workflowFile, stages, workflowName, stepName, yml, params)
        stage(stageName) {
          executeParameterizedStep(workflowFile, workflowName, stepName, params, yml)
        }
      } catch(e2) {
        error("""Encountered an error while executing: $workflowName: $stepName
                |--------------------------------------------------------------
                |$e2
                |--------------------------------------------------------------
                |${e2.getStackTrace().join('\n')}""".stripMargin())
      } finally {
        def stageEnd = System.currentTimeMillis()
        def stageTime = (stageEnd - stageStart)/1000
        println "Stage [${stageName}] took ${stageTime} seconds"
      }
    }
  }
}

private executeParameterizedStep(Object workflow, String sectionName, String stepName, Map stepValues, Map yml) {
  debugPrint([
    'workflow'      : workflow,
    'sectionName'   : sectionName,
    'stepName'      : stepName,
    'stepValues'    : stepValues,
    'yml'           : yml
  ])
  Boolean doubleMap = workflow.metaClass.respondsTo(workflow, stepName, Map, Map)
  Boolean singleMap = workflow.metaClass.respondsTo(workflow, stepName, Map)

  if (doubleMap) {
    debugPrint("Executing ${stepName} with yml and args".center(80, '-'), 2)
    workflow."${stepName}"(yml, stepValues)
  } else if (singleMap) {
    debugPrint("Executing ${stepName} with only yml".center(80, '-'), 2)
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

private getStageName(workflow, List stages, String workflowName, String stepName, Map yml, Map args) {
  def stageName
  Boolean canGenerate = workflow.metaClass.respondsTo(workflow, 'getStageName', Map, Map, String)
  if (canGenerate) {
    stageName = workflow.getStageName(yml, args, stepName)
    if (!stageName) {
      stageName = "$workflowName: $stepName"
    }
  } else {
    stageName = "$workflowName: $stepName"
  }
  def existingStageNames = stages.findAll{ it == stageName }
  if (existingStageNames.size() > 0) {
    stages.add(stageName)
    stageName = "$stageName (${existingStageNames.size()+1})"
  } else {
    stages.add(stageName)
  }
  return stageName
}

// Workflow loader
private loadWorkflows(String fileName, Map yml) {
  def repo                = yml.tools?.jenkins?.workflows?.repo         ?: env.WORKFLOW_REPOSITORY
  def branch              = yml.tools?.jenkins?.workflows?.branch       ?: yml.tools?.jenkins?.workflows?.tag ?: 'master'
  def credentialCriteria  = yml.tools?.jenkins?.workflows?.credentials  ?: ['description': env.WORKFLOW_GIT_CREDENTIAL_DESCRIPTION]
  def workflowDir         = yml.tools?.jenkins?.workflows?.directory    ?: 'workflows'
  def nodeLabel           = yml.tools?.jenkins?.workflows?.label        ?: 'linux'

  assert fileName : "workflowLibs :: loadWorkflows :: no Filename provided to load, perhaps this is an error with "

  debugPrint(['fileName' : fileName, 'repo' : repo, 'branch' : branch, 'credentialCriteria' : credentialCriteria], 2)

  fileName = "${fileName}.groovy"
  def localFile = "$workflowDir/$fileName"
  def localFileExists = fileExists localFile
  debugPrint(['localFile' : localFile,'localFileExists' : localFileExists])

  def workflow
  if (localFileExists) {
    try {
      workflow = load localFile
      println """${'*'*80}
                |${Constants.Colors.YELLOW_ON_BLACK}Loaded Custom Workflow [${Constants.Colors.CYAN_ON_BLACK}$localFile${Constants.Colors.YELLOW_ON_BLACK}].${Constants.Colors.CLEAR}
                |${'*'*80}""".stripMargin()
    } catch (NotSerializableException nse) {
      error("""${Constants.Colors.RED}
              |Error loading workflow, this is most likely to be caused by a syntax error in the groovy file.
              |-----------------------------------------------------------------------------------------------
              |Common errors include missing/incomplete or incomplete syntax.
              |Examples
              |1. `def x =` # the var needs to be set to something
              |2. `def s = 'this is a string" # Open and close quotes should match. Single quote should be used unless you are doing interpolation.
              |-----------------------------------------------------------------------------------------------
              |Defining variable named the same as an existing variable
              |Examples
              |1. `def v = 1; def v = 3; # rename second variable if both are needed or drop the def to reassign the existing variable
              |2. `concurPipeline = new com.concur.Commands(); def concurPipeline = new com.concur.Commands(); # If def is not used variable is global.
              |-----------------------------------------------------------------------------------------------
              |Using a library function without importing. Using a function from workflowLibs without defining a variable.
              |1. `concurUtil.mustacheReplaceAll("Commit SHA - {{ commit_sha }}") # Ensure a definition for concurUtil exists eg `def concurUtil = new com.concur.Util()`
              |2. 'Yaml().load("<<string content>>")' # The Snake YAML library needs to be imported first eg `import org.yaml.snakeyaml.*;`
              ${Constants.Colors.CLEAR}
              """.stripMargin())
    }
  } else {
    assert repo : 'Repo to checkout for workflows not set under tools.jenkins.workflows.repo or as the environment variable: WORKFLOW_REPOSITORY.'
    // only search for credential if needed
    def credentialsId = getCredentialsWithCriteria(credentialCriteria).id
    assert credentialsId
    debugPrint(credentialsId)
    try {
      workflow = fileLoader.fromGit(fileName, repo, branch, credentialsId, nodeLabel)
      println """${'*'*80}
                |${Constants.Colors.WHITE_ON_BLACK}Loaded Workflow [${Constants.Colors.CYAN_ON_BLACK}${fileName}${Constants.Colors.WHITE_ON_BLACK}] from remote [${Constants.Colors.CLEAR}${repo}${Constants.Colors.WHITE_ON_BLACK}].${Constants.Colors.CLEAR}
                |${'*'*80}""".stripMargin()
    } catch (NotSerializableException nse) {
      error("Failed to load the [$fileName] workflow from $repo, please create an issue on the project in GitHub (https://github.com/concur/jenkins-workflow).")
    }
  }
  assert workflow : "Workflow file $fileName not found or unable to load from remote repo."

  return workflow
}

/*
description: Check branch patterns against what is available in a YAML file, uses regular expressions to match
examples:
  - |
    def concurCommands = new com.concur.Commands()
    def yaml = readYaml 'pipelines.yml'
    concurCommands.checkBranch(yaml.pipelines, 'master')
 */
def checkBranch(Map yml, String branch=env.BRANCH_NAME) {
  assert yml    : 'Couldn\'t find pipelines.yml.'
  assert branch : 'Branch name not set. This should typically be set by the environment as BRANCH_NAME. Please ensure this is being called within a node and that you are in a job type that provides sets the environment variable automatically such as a Multibranch pipeline.'

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

  debugPrint(['branch' : branch, 'patterns' : patterns, 'branchType': branchType], 2)

  return branchType
}

// ########################
// # Jenkins Credentials
// ########################

/*
description: Get the credentials based on criteria defined in a map
examples:
  - |
    // Find credential by description
    def concurCommands = new com.concur.Commands()
    println concurCommands.getCredentialsWithCriteria(['description': 'Example credential def']).id
    // b709b4ac-f2f6-4e54-aca3-002270a92657
  - |
    // Find only SSH credentials with a particular description
    def concurCommands = new com.concur.Commands()
    println concurCommands.getCredentialsWithCriteria(['description': 'Example credential def', 'class': com.concur.CredentialTypes.sshPrivateKey]).id
    // 1ae2ff9b-0d8a-4f75-ac21-8368c983d607
 */
def getCredentialsWithCriteria(Map criteria) {
  // Make sure criteria isn't empty
  assert criteria : 'WorkflowLibs :: Commands :: getCredentialsWithCriteria :: No criteria provided to search for, please use this function with a Map like such `new com.concur.Commands().getCredentialsWithCriteria([\'description\': \'example credential description\']`.'

  debugPrint(criteria, 2)

  if (criteria.keySet().contains('class')) {
    assert criteria."class".class != String : "java.lang.String is not a valid class for credentials"
    criteria."class" = criteria."class".class == CredentialTypes ? criteria."class"?.getValue() : criteria."class"
    assert criteria."class" in CredentialTypes.getValues() : "Credential type ${criteria.'class'} is not supported or is invalid."
  }

  def credentials = []

  // Get all of the global credentials
  def globalCreds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
    com.cloudbees.plugins.credentials.impl.BaseStandardCredentials.class,
    jenkins.model.Jenkins.instance)
  credentials = intersectCredentials(criteria, globalCreds)
  debugPrint([
    'globalCreds'       : globalCreds.collect { ['description': it.description, 'id': it.id] },
    'criteria'          : criteria,
    'found credentials' : credentials.collect { ['description': it.description, 'id': it.id] }
  ])

  // Only search through folder credentials if we can't find a global
  if (!credentials) {
    // Get credentials for the folder that the job is in
    ArrayList folderCreds = new ArrayList()
    def folderNames = env.JOB_NAME ?: ""
    def folders = folderNames.split('/')
    for (int i = 0; i < folders.size(); i++) {
      def folderName = folders[0..i].join('/')
      try {
        getFolderCredentials(folderName).each { n ->
          folderCreds << n
          debugPrint(folderCreds, 2)
        }
      } catch (Exception e) { }
    }
    debugPrint([
      'folderCreds': folderCreds.collect { ['description': it.description, 'id': it.id] },
    ])
    credentials = intersectCredentials(criteria, folderCreds)
  }
  // Fail if no credentials are found that match the criteria
  assert credentials : """No credentials found that match your criteria: ${criteria}"""
  assert credentials.size() == 1 : """
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

private intersectCredentials(Map criteria, List credentialList) {
  def credentials = []
  def count = criteria.keySet().size()
  for (c in credentialList) {
    def i = 0
    if (count == c.getProperties().keySet().intersect(criteria.keySet()).size()) {
      if (c.getProperties().keySet().intersect(criteria.keySet()).equals(criteria.keySet())) {
        for (p in c.getProperties().keySet().intersect(criteria.keySet())) {
          if (c."${p}" != criteria."${p}") {
            break
          } else {
            i++
          }
        }
      }
    }
    if (i == count) {
      credentials << c
    }
  }
  return credentials
}

// Get credentials for a given folder name
private getFolderCredentials(String folderName) {
  return AbstractFolder.class
                       .cast(Jenkins.instance.getItemByFullName(folderName))
                       .getProperties()
                       .get(FolderCredentialsProperty.class)
                       .getCredentials()
}

/*
description: Execute contents of a Closure with an appropriate credential wrapper. For a username/password credential the username will be an environment variable called CRED_USERNAME and the password will be CRED_PASSWORD. For a secret text password type the environment variable will be called CRED_SECRET. SSH credentials get put into an SSH agent and should be available to use without specifying a path to the key.
examples:
  - |
    // Execute an SSH Command 
    def concurCommands = new com.concur.Commands()
    concurCommands.executeWithCredentials(['description': 'Example credential def', 'class': com.concur.CredentialTypes.sshPrivateKey], { sh "ssh user@example.local uname -a" })
    // Linux example 4.4.0-97-generic #120-Ubuntu SMP Tue Sep 19 17:28:18 UTC 2017 x86_64 x86_64 x86_64 GNU/Linux
  - |
    // Use username and password
    def concurCommands = new com.concur.Commands()
    concurCommands.executeWithCredentials(['description': 'Example credential def'], { powershell '''
        $username = "$env:CRED_USERNAME"
        $password = "$env:CRED_PASSWORD"
        $secureStringPwd = $password | ConvertTo-SecureString -AsPlainText -Force 
        $creds = New-Object System.Management.Automation.PSCredential -ArgumentList $user, $secureStringPwd
        Invoke-Command -Credential $creds -Computername "remote.example.local" -Scriptblock { Write-Host "Hello from $($env:COMPUTERNAME)" }''' })
 */
public executeWithCredentials(Map credentialDef, Closure func) {
  def credential = getCredentialsWithCriteria(credentialDef)

  switch(credential.getClass()) {
    case com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl:
      debugPrint("WorkflowLibs :: ConcurCommands :: executeWithCredentials", "Using Username and Password")
      withCredentials([usernamePassword(credentialsId: credential.id, passwordVariable: 'CREDS_PASSWORD',usernameVariable: 'CRED_USERNAME')]) {
        func()
      }
      break
    case com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey:
    debugPrint("WorkflowLibs :: ConcurCommands :: executeWithCredentials", "Using sshagent")
      sshagent([credential.id]) {
        func()
      }
      break
    case org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl:
    debugPrint("WorkflowLibs :: ConcurCommands :: executeWithCredentials", "Using StringCredentials")
      withCredentials([string(credentialsId: credential.id, variable: 'CRED_SECRET')]) {
        func()
      }
      break
    default:
      error("WorkflowLibs :: ConcurCommands :: executeWithCredentials :: Credential does not match a supported type")
  }
}

// Convert to a serializable list
@NonCPS
def jenkinsMap(Map gmap){
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

/*
description: Get the version number for the provided plugin name
examples:
  - |
    println new com.concur.Commands().getPluginVersion('pipeline-githubnotify-step')
    // 1.0.3
  - |
    println new com.concur.Commands().getPluginVersion('blueocean-dashboard')
    // 1.3.5
 */
def getPluginVersion(String pluginShortName) {
  assert pluginShortName : "Plugin name must be provided."
  return Jenkins.getInstance().pluginManager.getPlugin("${pluginShortName}")?.getVersion()
}

/*
description: Return a string of the stack trace, this is blocked by default by Jenkins
examples:
  - |
    try {
      error('m')
    } catch (e) {
      println new com.concur.Commands().getJavaStackTrace(e)
    }
    // org.jenkinsci.plugins.workflow.steps.ErrorStep$Execution.run(ErrorStep.java:63)
    // org.jenkinsci.plugins.workflow.steps.ErrorStep$Execution.run(ErrorStep.java:50)....
 */
def getJavaStackTrace(Throwable e) {
  return e.getStackTrace().join('\n')
}

/*
description: Check the environment to see if we are in debug mode
examples:
  - |
    println new com.concur.Commands().isDebug()
    // false
  - |
    env."${com.concur.Constants.Env.DEBUG}" = true
    println new com.concur.Commands().isDebug()
    // true
 */
def getPipelineDataFile(String fileName = 'pipelines.yml', String format = 'yml', String baseNode = 'pipelines') {
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
    env."${Constants.Env.DEBUG}"        = true // this being set allows for a lot of debugging output to be included.
    env."${Constants.Env.DEBUG_LEVEL}"  = (dataMap?."${baseNode}"?.general.debugLevel as int) ?: 1
  }
  return dataMap[baseNode]
}

/*
description: Check the environment to see if we are in debug mode
examples:
  - |
    println new com.concur.Commands().isDebug()
    // false
  - |
    env."${com.concur.Constants.Env.DEBUG}" = true
    println new com.concur.Commands().isDebug()
    // true
 */
def isDebug() {
  return env."${Constants.Env.DEBUG}"?.toBoolean() ?: false
}

/*
description: Print a string of data to the Jenkins console output, only if the user wants to get debug information. Allows developers to set a specific title.
example: |
    def concurCommands = new com.concur.Commands()
    env.DEBUG_MODE = true
    concurCommands.debugPrint('example message')
    // Console output will show
    // ### Debug output for [Script1] ###
    // ### Debug >>> example message
    // ### End Debug ###
 */
public debugPrint(String title, Map msgdata, int debugLevelToPrint=1) {
  def str = "### ${Constants.Colors.MAGENTA}Debug output for [${Constants.Colors.BLUE}${title}${Constants.Colors.MAGENTA}]${Constants.Colors.CLEAR} ###"
  msgdata.each { data ->
    str += "\n### ${Constants.Colors.MAGENTA}Debug >>> ${Constants.Colors.CYAN}${data.key}: ${data.value}${Constants.Colors.CLEAR}"
  }
  str += "\n### ${Constants.Colors.MAGENTA}End Debug${Constants.Colors.CLEAR} ###"
  debugPrintMessage(str, debugLevelToPrint)
}

/*
description: Print a map of data to the Jenkins console output, only if the user wants to get debug information, title will be automatically generated
example: |
    def concurCommands = new com.concur.Commands()
    env.DEBUG_MODE = true
    concurCommands.debugPrint(['example': 'message'])
    // Console output will show
    // ### Debug output for [Script1] ###
    // ### Debug >>> example: message
    // ### End Debug ###
 */
public debugPrint(Map msgdata, int debugLevelToPrint=1) {
  def str = "### ${Constants.Colors.MAGENTA}Debug output for [${Constants.Colors.BLUE}${getDebugMessageTitle()}${Constants.Colors.MAGENTA}]${Constants.Colors.CLEAR} ###"
  msgdata.each { data ->
    str += "\n### ${Constants.Colors.MAGENTA}Debug >>> ${Constants.Colors.CYAN}${data.key}: ${data.value}${Constants.Colors.CLEAR}"
  }
  str += "\n### ${Constants.Colors.MAGENTA}End Debug${Constants.Colors.CLEAR} ###"
  debugPrintMessage(str, debugLevelToPrint)
}

/*
description: Print a list of data to the Jenkins console output, only if the user wants to get debug information, title will be automatically generated
example: |
    def concurCommands = new com.concur.Commands()
    env.DEBUG_MODE = true
    concurCommands.debugPrint(['example', 'message'])
    // Console output will show
    // ### Debug output for [Script1] ###
    // ### Debug >>> example
    // ### Debug >>> message
    // ### End Debug ###
 */
public debugPrint(List msgdata, int debugLevelToPrint=1) {
  def str = "### ${Constants.Colors.MAGENTA}Debug output for [${Constants.Colors.BLUE}${getDebugMessageTitle()}${Constants.Colors.MAGENTA}]${Constants.Colors.CLEAR} ###"
  msgdata.each { data ->
    str += "\n### ${Constants.Colors.MAGENTA}Debug >>> ${Constants.Colors.CYAN}${data}${Constants.Colors.CLEAR}"
  }
  str += "\n### ${Constants.Colors.MAGENTA}End Debug${Constants.Colors.CLEAR} ###"
  debugPrintMessage(str, debugLevelToPrint)
}

/*
description: Print a string of data to the Jenkins console output, only if the user wants to get debug information, title will be automatically generated
example: |
    def concurCommands = new com.concur.Commands()
    env.DEBUG_MODE = true
    concurCommands.debugPrint('example message')
    // Console output will show
    // ### Debug output for [Script1] ###
    // ### Debug >>> example message
    // ### End Debug ###
 */
public debugPrint(String msgdata, int debugLevelToPrint=1) {
  debugPrintMessage("""### ${Constants.Colors.MAGENTA}Debug output for [${Constants.Colors.BLUE}${getDebugMessageTitle()}${Constants.Colors.MAGENTA}]${Constants.Colors.CLEAR} ###
                      |### ${Constants.Colors.MAGENTA}Debug >>> ${Constants.Colors.CYAN}${msgdata}${Constants.Colors.CLEAR}
                      |### ${Constants.Colors.MAGENTA}End Debug${Constants.Colors.CLEAR} ###""".stripMargin(), debugLevelToPrint)
}

private getDebugMessageTitle() {
  def cMethod = org.codehaus.groovy.runtime.StackTraceUtils.sanitize(new Throwable()).stackTrace[2]
  return "WorkflowLibs :: ${cMethod.declaringClass} :: ${cMethod.methodName} :: Line ${cMethod.lineNumber}"
}

private debugPrintMessage(String msg, int debugLevelToPrint=1) {
  if (isDebug()) {
    if (debugShouldPrint(debugLevelToPrint)) {
      println msg
    }
  }
}

private debugShouldPrint(int debugLevelToPrint) {
  int currentRequestedLevel = env."${Constants.Env.DEBUG_LEVEL}" as int
  return (debugLevelToPrint <= currentRequestedLevel)
}
