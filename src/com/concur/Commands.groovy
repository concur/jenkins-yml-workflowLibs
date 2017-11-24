#!/usr/bin/env groovy

// ######### Workflow Execution

// ########################
// Workflow Methods
// ########################

// Run the workflow steps for the appropriate sections
def runSteps(yml, branch = env.BRANCH_NAME) {
  branch = checkBranch(yml,branch)
  assert branch : """|Unable to determine steps to take for branch ${env.BRANCH_NAME}
                     |----------------------------------------
                     |Please ensure your yml file contains a section under tools.github.patterns
                     |pipelines:
                     |  tools:
                     |    github:
                     |      patterns:
                     |        master: master
                     |        develop: develop
                     |        feature: .+
                     |        """.stripMargin()

  assert yml

  debugPrint("WorkflowLibs :: Commands :: runSteps", [
    "branch": branch,
    "yml": yml,
  ])

  def workflows = yml?.branches?."$branch"?.steps

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
    executeWorkflows(workflowsList, yml)
  } catch(e) {
    currentBuild.result = 'FAILED'
    throw e
  }
}

private executeWorkflows(workflows, yml) {
  def stages = []
  def stageNum = 0
  workflows.each { workflow ->
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
            if (step instanceof String || step instanceof GStringImpl) {
              debugPrint('WorkflowLibs :: Commands :: executeWorkflows :: basic step', ['workflowName': workflowName,
                                                            'stepName': step])
              workflowFile."${step}"()
            } else if (step instanceof Map) {
              def params = step[stepName]
              debugPrint('WorkflowLibs :: Commands :: executeWorkflows :: parameterized step', ['workflowName': workflowName,
                                                                    'stepName': stepName,
                                                                    'params': params])
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
}

private executeParameterizedStep(workflow, sectionName, stepName, stepValues, yml) {
  debugPrint('WorkflowLibs :: Commands :: executeParameterizedStep :: parameters', [
    'workflow':workflow,
    'sectionName': sectionName,
    'stepName':stepName,
    'stepValues':stepValues,
    'yml':yml]
  )
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

// Workflow loader
private loadWorkflows(fileName, yml) {
  def repo                = yml?.tools?.buildhub?.workflows?.repo         ?: 'https://github.concur.com/jenkins-util/workflows.git'
  def branch              = yml?.tools?.buildhub?.workflows?.branch       ?: yml?.tools?.buildhub?.workflows?.tag ?: 'master'
  def credentialCriteria  = yml?.tools?.buildhub?.workflows?.credentials  ?: ['description' : 'Primary GitHub clone/checkout credentials', 'class' : CredentialTypes.usernamePassword]
  def workflowDir         = yml?.tools?.buildhub?.workflows?.directory    ?: 'workflows'
  def nodeLabel           = yml?.tools?.buildhub?.workflows?.label        ?: 'linux'

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
    // only search for credential if needed
    def credentialsId = getCredentialsWithCriteria(credentialCriteria).id
    assert credentialsId
    debugPrint('WorkflowLibs :: Commands :: loadWorkflows :: credentials', credentialsId)
    workflow = fileLoader.fromGit(fileName,repo, branch, credentialsId, nodeLabel)
  }
  assert workflow : "Workflow file ${fileName} not found or unable to load."

  return workflow
}

// Check branch pattern
def checkBranch(yml, branch=env.BRANCH_NAME) {
  assert yml    : "Couldn't find pipelines.yml"
  assert branch : "Branch name not set."
  debugPrint("WorkflowLibs :: Commands :: checkBranch :: branch", branch)

  def patterns = yml?.tools?.github?.patterns
  assert patterns
  debugPrint("WorkflowLibs :: Commands :: checkBranch :: patterns", patterns)

  def branchType = patterns.find {
    branch.matches(it.value)
  }.key

  return branchType
}

// ######### Helpers

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
