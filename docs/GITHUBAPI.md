# com.concur.GitHubApi

## githubRequestWrapper(String, String, Map, Map, String, Boolean, Boolean, String)

> Wrapper for contacting the GitHub API v3, this will load the credential, set the Authorization header and convert any post data to JSON. If you want to make requests against the GraphQL endpoint see githubGraphqlRequestWrapper. More information is available at [developer.github.com/v3/](https://developer.github.com/v3/)

| Type    | Name              | Default   |
|:--------|:------------------|:----------|
| String  | method            |           |
| String  | endpoint          |           |
| Map     | postData          | [:]       |
| Map     | additionalHeaders | [:]       |
| String  | credentialId      | ''        |
| Boolean | outputResponse    | false     |
| Boolean | ignoreErrors      | false     |
| String  | host              | null      |

### Example 1

```groovy
def concurGitHub = new com.concur.GitHubApi()
println concurGitHub.githubRequestWrapper('GET', '/user/repos')
// [{"name": "jenkins-yml-workflowLibs","full_name": "concur/jenkins-yml-workflowLibs","owner": {"login": "concur",....
```

## githubGraphqlRequestWrapper(String, Map, String, String, Boolean, Boolean)

> Wrapper for contacting the GitHub v4 API, this will load the credential, set the Authorization header and convert setup the post data for the query with variables if provided. This is specific to the v4 (GraphQL) api, for the rest API use githubRequestWrapper. More information about the API can be found at [developer.github.com/v4/](https://developer.github.com/v4/)

| Type    | Name            | Default   |
|:--------|:----------------|:----------|
| String  | query           |           |
| Map     | variables       | null      |
| String  | host            | null      |
| String  | credentialId    | null      |
| Boolean | outputResponse  | false     |
| Boolean | ignoreSslErrors | false     |

### Example 1

```groovy
def concurGitHub = new com.concur.GitHubApi()
def query = '''query{
                viewer{
                  login
                }
              }'''
println concurGitHub.githubGraphqlRequestWrapper('query')
// {"data": {"viewer": {"login": "concur"}}}
```

## getPullRequests(Map, String, String, String, String, String, String)

> Get a list of all pull requests for a given repository. Information is retrieved using the GraphQL API so not all data is returned. Provided credentials must have at least read access.

| Type   | Name           | Default   |
|:-------|:---------------|:----------|
| Map    | credentialData |           |
| String | owner          | ''        |
| String | repo           | ''        |
| String | host           | ''        |
| String | fromBranch     | ''        |
| String | baseBranch     | ''        |
| String | state          | 'OPEN'    |

### Example

```groovy
println new com.concur.GitHubApi().getPullRequests(['description': 'Example Github token'], 'concur', 'jenkins-yml-workflowLibs')
// [{"id":"MDExOlB1bGxSZXF1ZXN0MTUzMzk0NTk1","number":1,"title":"...","headRefName":"develop","baseRefName":"master","labels":{"nodes":[]},"mergeable":"UNKNOWN"}...]
```

## getReleases(Map, String, String, String, int)

> Get a list of $limit number of releases. Information is retrieved using the GraphQL API so not all data is returned. Provided credentials must have at least read access.

| Type   | Name           | Default   |
|:-------|:---------------|:----------|
| Map    | credentialData |           |
| String | owner          | ''        |
| String | repo           | ''        |
| String | host           | ''        |
| int    | limit          | 10        |

### Example

```groovy
println new com.concur.GitHubApi().getReleases(['description': 'Example Github token'], 'concur', 'jenkins-yml-workflowLibs')
// [{"tag":{"name":"v0.2.0","target":{"oid":"b828c94aba486ac0416bf95e387d860b79e6343f"}},"createdAt":"2018-01-07T01:37:49Z","isPrerelease":false,"name":"v0.2.0"}...]
```

## createPullRequest(String, String, String, String, String, String, Map, String, Boolean)

> Create a pull request for the specified repository. Provided credentials need write access. Uses Rest API for this call, more information provided at [developer.github.com/v3/pulls/#create-a-pull-request](https://developer.github.com/v3/pulls/#create-a-pull-request)

| Type    | Name                  | Default        |
|:--------|:----------------------|:---------------|
| String  | title                 |                |
| String  | fromBranch            |                |
| String  | toBranch              |                |
| String  | owner                 |                |
| String  | repo                  |                |
| String  | host                  |                |
| Map     | credentialData        |                |
| String  | summary               | "Automatically |
| Boolean | maintainer_can_modify | true           |

### Example

```groovy
println new com.concur.GitHubApi().createPullRequest('Example PR Title', 'develop', 'master', 'concur', 'jenkins-yml-workflowLibs', 'github.com', ['description': 'example GitHub credentials'])
// {"id":1,"url":"https://api.github.com/repos/concur/jenkins-yml-workflowLibs/pulls/1347","html_url":"https://github.com/concur/jenkins-yml-workflowLibs/pull/1347".....
```

## createRelease(Map, String, String, String, Boolean, Boolean, String, String, String, String)

> Create a new release with release notes. Uses Rest API, more information at [developer.github.com/v3/repos/releases/#create-a-release](https://developer.github.com/v3/repos/releases/#create-a-release)

| Type    | Name           | Default        |
|:--------|:---------------|:---------------|
| Map     | credentialData |                |
| String  | notes          |                |
| String  | tag            |                |
| String  | name           |                |
| Boolean | preRelease     | false          |
| Boolean | draft          | false          |
| String  | commitish      | env.GIT_COMMIT |
| String  | owner          | ''             |
| String  | repo           | ''             |
| String  | host           | ''             |

### Example 1

```groovy
// create a new full release
println new com.concur.GitHubApi().createRelease(['description': 'example GitHub credential'], '### Added\n\n* New Feature A', 'v0.1.0', 'v0.1.0')
// {"url": "https://api.github.com/repos/concur/jenkins-yml-workflowLibs/releases/1","html_url": "https://github.com/concur/jenkins-yml-workflowLibs/releases/v0.1.0"...

```

### Example 2

```groovy
// createa a draft release
println new com.concur.GitHubApi().createRelease(['description': 'example GitHub credential'], '### Added\n\n* New Feature A', 'v0.1.0', 'v0.1.0', false, true)
// {"url": "https://api.github.com/repos/concur/jenkins-yml-workflowLibs/releases/1","html_url": "https://github.com/concur/jenkins-yml-workflowLibs/releases/v0.1.0"...

```

### Example 3

```groovy
// create a pre-release
println new com.concur.GitHubApi().createRelease(['description': 'example GitHub credential'], '### Added\n\n* New Feature A', 'v0.1.0', 'v0.1.0', true false)
// {"url": "https://api.github.com/repos/concur/jenkins-yml-workflowLibs/releases/1","html_url": "https://github.com/concur/jenkins-yml-workflowLibs/releases/v0.1.0"...
```