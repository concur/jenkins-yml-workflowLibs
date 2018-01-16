# com.concur.Commands

## runSteps(Map, String)

> Run the workflow steps for the appropriate sections

| Type   | Name   | Default         |
|:-------|:-------|:----------------|
| Map    | yml    |                 |
| String | branch | env.BRANCH_NAME |

### Example 1

```groovy
def concurCommands = new com.concur.Commands()
def yaml = readYaml 'pipelines.yml'
concurCommands.runSteps(yaml.pipelines)
```

## checkBranch(Map, String)

> Check branch patterns against what is available in a YAML file, uses regular expressions to match

| Type   | Name   | Default         |
|:-------|:-------|:----------------|
| Map    | yml    |                 |
| String | branch | env.BRANCH_NAME |

### Example 1

```groovy
def concurCommands = new com.concur.Commands()
def yaml = readYaml 'pipelines.yml'
concurCommands.checkBranch(yaml.pipelines, 'master')
```

## getCredentialsWithCriteria(Map)

> Get the credentials based on criteria defined in a map

| Type   | Name     | Default   |
|:-------|:---------|:----------|
| Map    | criteria |           |

### Example 1

```groovy
// Find credential by description
def concurCommands = new com.concur.Commands()
println concurCommands.getCredentialsWithCriteria(['description': 'Example credential def']).id
// b709b4ac-f2f6-4e54-aca3-002270a92657

```

### Example 2

```groovy
// Find only SSH credentials with a particular description
def concurCommands = new com.concur.Commands()
println concurCommands.getCredentialsWithCriteria(['description': 'Example credential def', 'class': com.concur.CredentialTypes.sshPrivateKey]).id
// 1ae2ff9b-0d8a-4f75-ac21-8368c983d607
```

## executeWithCredentials(Map, Closure)

> Execute contents of a Closure with an appropriate credential wrapper. For a username/password credential the username will be an environment variable called CRED_USERNAME and the password will be CRED_PASSWORD. For a secret text password type the environment variable will be called CRED_SECRET. SSH credentials get put into an SSH agent and should be available to use without specifying a path to the key.

| Type    | Name          | Default   |
|:--------|:--------------|:----------|
| Map     | credentialDef |           |
| Closure | func          |           |

### Example 1

```groovy
// Execute an SSH Command 
def concurCommands = new com.concur.Commands()
concurCommands.executeWithCredentials(['description': 'Example credential def', 'class': com.concur.CredentialTypes.sshPrivateKey], { sh "ssh user@example.local uname -a" })
// Linux example 4.4.0-97-generic #120-Ubuntu SMP Tue Sep 19 17:28:18 UTC 2017 x86_64 x86_64 x86_64 GNU/Linux

```

### Example 2

```groovy
// Use username and password
def concurCommands = new com.concur.Commands()
concurCommands.executeWithCredentials(['description': 'Example credential def'], { powershell '''
    $username = "$env:CRED_USERNAME"
    $password = "$env:CRED_PASSWORD"
    $secureStringPwd = $password | ConvertTo-SecureString -AsPlainText -Force 
    $creds = New-Object System.Management.Automation.PSCredential -ArgumentList $user, $secureStringPwd
    Invoke-Command -Credential $creds -Computername "remote.example.local" -Scriptblock { Write-Host "Hello from $($env:COMPUTERNAME)" }''' })
```

## getPluginVersion(String)

> Get the version number for the provided plugin name

| Type   | Name            | Default   |
|:-------|:----------------|:----------|
| String | pluginShortName |           |

### Example 1

```groovy
println new com.concur.Commands().getPluginVersion('pipeline-githubnotify-step')
// 1.0.3

```

### Example 2

```groovy
println new com.concur.Commands().getPluginVersion('blueocean-dashboard')
// 1.3.5
```

## getJavaStackTrace(Throwable)

> Return a string of the stack trace, this is blocked by default by Jenkins

| Type      | Name   | Default   |
|:----------|:-------|:----------|
| Throwable | e      |           |

### Example 1

```groovy
try {
  error('m')
} catch (e) {
  println new com.concur.Commands().getJavaStackTrace(e)
}
// org.jenkinsci.plugins.workflow.steps.ErrorStep$Execution.run(ErrorStep.java:63)
// org.jenkinsci.plugins.workflow.steps.ErrorStep$Execution.run(ErrorStep.java:50)....
```

## getPipelineDataFile(String, String, String)

> Check the environment to see if we are in debug mode

| Type   | Name     | Default   |
|:-------|:---------|:----------|
| String | fileName |           |
| String | format   |           |
| String | baseNode |           |

### Example 1

```groovy
println new com.concur.Commands().isDebug()
// false

```

### Example 2

```groovy
env."${com.concur.Constants.Env.DEBUG}" = true
println new com.concur.Commands().isDebug()
// true
```

## debugPrint(String, Map, int)

> Print a string of data to the Jenkins console output, only if the user wants to get debug information. Allows developers to set a specific title.

| Type   | Name              | Default   |
|:-------|:------------------|:----------|
| String | title             |           |
| Map    | msgdata           |           |
| int    | debugLevelToPrint | 1         |

### Example

```groovy
def concurCommands = new com.concur.Commands()
env.DEBUG_MODE = true
concurCommands.debugPrint('example message')
// Console output will show
// ### Debug output for [Script1] ###
// ### Debug >>> example message
// ### End Debug ###
```

## debugPrint(Map, int)

> Print a map of data to the Jenkins console output, only if the user wants to get debug information, title will be automatically generated

| Type   | Name              | Default   |
|:-------|:------------------|:----------|
| Map    | msgdata           |           |
| int    | debugLevelToPrint | 1         |

### Example

```groovy
def concurCommands = new com.concur.Commands()
env.DEBUG_MODE = true
concurCommands.debugPrint(['example': 'message'])
// Console output will show
// ### Debug output for [Script1] ###
// ### Debug >>> example: message
// ### End Debug ###
```

## debugPrint(List, int)

> Print a list of data to the Jenkins console output, only if the user wants to get debug information, title will be automatically generated

| Type   | Name              | Default   |
|:-------|:------------------|:----------|
| List   | msgdata           |           |
| int    | debugLevelToPrint | 1         |

### Example

```groovy
def concurCommands = new com.concur.Commands()
env.DEBUG_MODE = true
concurCommands.debugPrint(['example', 'message'])
// Console output will show
// ### Debug output for [Script1] ###
// ### Debug >>> example
// ### Debug >>> message
// ### End Debug ###
```

## debugPrint(String, int)

> Print a string of data to the Jenkins console output, only if the user wants to get debug information, title will be automatically generated

| Type   | Name              | Default   |
|:-------|:------------------|:----------|
| String | msgdata           |           |
| int    | debugLevelToPrint | 1         |

### Example

```groovy
def concurCommands = new com.concur.Commands()
env.DEBUG_MODE = true
concurCommands.debugPrint('example message')
// Console output will show
// ### Debug output for [Script1] ###
// ### Debug >>> example message
// ### End Debug ###
```