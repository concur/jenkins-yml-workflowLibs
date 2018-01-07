# com.concur.Git

## getCommitSHA(String, int)

> Get the commit SHA for the last file or folder changed.

| Type   | Name   | Default   |
|:-------|:-------|:----------|
| String | folder | '.'       |
| int    | depth  | 1         |

## getFilesChanged(String)

> Get a list of files that were changed in the current commit.

| Type   | Name      | Default   |
|:-------|:----------|:----------|
| String | commitSha | ''        |

## runGitShellCommand(String, String)

> Run a command, set the command for Linux and Windows and this method will determine which one to use.

| Type   | Name          | Default   |
|:-------|:--------------|:----------|
| String | gitCommand    |           |
| String | winGitCommand | ''        |

## saveGitProperties(Map)

> Save git properties to environment variables

| Type   | Name    | Default   |
|:-------|:--------|:----------|
| Map    | scmVars |           |

### Example

```groovy
new com.concur.Git().saveGitProperties()
sh "env"
// GIT_SHORT_COMMIT=b828c9
// GIT_COMMIT=b828c94aba486ac0416bf95e387d860b79e6343f
// GIT_URL=git@github.com:concur/jenkins-yml-workflowLibs
// GIT_COMMIT_MESSAGE=Fix workflow version lock.
// GIT_AUTHOR=Nic Patterson
// GIT_EMAIL=arasureynn@gmail.com
// GIT_PREVIOUS_COMMIT=597563389d144c7098dd3b71b1fc1e600b215ff7
// GIT_OWNER=concur
// GIT_HOST=github.com
// GIT_REPO=jenkins-yml-workflowLibs
// ....
```

## getGitData(String)

> Get the Git owner, repo and host

| Type   | Name   | Default   |
|:-------|:-------|:----------|
| String | url    |           |

### Example 1

```groovy
println new com.concur.Git().getGitData('https://github.com/concur/jenkins-yml-workflowLibs.git')
// ['host': 'github.com', 'owner': 'concur', 'repo': 'jenkins-yml-workflowLibs']

```

### Example 2

```groovy
println new com.concur.Git().getGitData('https://github.example.com/awesome/repo.git')
// ['host': 'github.example.com', 'owner': 'awesome', 'repo': 'repo']
```

## getVersion(String, String, Boolean)

> Determine a version number based on the current latest tag in the repository. Will automatically increment the minor version and append a build version.

| Type    | Name           | Default   |
|:--------|:---------------|:----------|
| String  | version        |           |
| String  | scheme         |           |
| Boolean | ignorePrevious |           |

### Example 1

```groovy
// Latest tag in the repo is 1.3.1 and it was tagged 5 hours ago
println new com.concur.Git().getVersion()
// 1.4.0-0018000000

```

### Example 2

```groovy
// New repo with no tags, repository was created 1 hour ago
println new com.concur.Git().getVersion()
// 0.1.0-0003600000

```

### Example 3

```groovy
// No tags in repo, override default version, created 18 days ago
println new com.concur.Git().getVersion('3.6.9')
// 3.7.0-1555200000
```