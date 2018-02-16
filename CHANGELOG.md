# Jenkins-YML-WorkflowLibs changes by release

## 0.4.0

### Added

* Add SHORT_VERSION to default replacement option, this is the version provided by Git().getVersion() without the build number.

### Updated

* Git.getVersion: Update to be more flexible with tag retrieval, will now get latest from the current branch using `git describe --tags`
* Git.timeSinceLatestTag: Signature changed to accept a tag string that is used in the underlying git command.

## 0.3.0

### Added

* Added script to automatically generate documentation on commit.
* Update com.concur.* groovy files with documentation.
* Updated documentation in vars/*.txt, these are viewable in Pipeline Syntax -> Global Variable Reference from a pipeline job.
* Add to setup.groovy script to set Markup formatter to Safe HTML so the vars documentation shows appropriately.

## 0.2.0

### Added

* scripts/setup.groovy: Will configure a Jenkins instance to use these workflows/workflow libraries. Including adding as a global library, adding environment variables and adding a SSH credential.
* Util.parseChangelog: Reads a changelog file to determine what releases are in it.
* GitHubApi.getReleases: Use GitHub's GraphQL API to get information about a repositories releases.
* GitHubApi.createRelease: Create a release in GitHub with release notes.

### Changed

* Working towards a more strongly typed system, updated vars from `def` to a static type in many areas.
* Commands.getStageName: Added a default stage name if the method is not available or doesn't return anything.
* All: Adding more descriptive assertion/errors so it is easier to determine where an error is happening.
* Commands.getCredentialsWithCriteria: Will search global credentials first and return the credential if found before looking through the folder structure.
* Commands.getJavaStackTrace: If provided a `throwable` will return the stacktrace for it as a `\n` joined string. `throwable.getStackTrace()` is blocked by Jenkins by default.
* Git.runGitShellCommand: Uses `powershell` or `sh` step with `returnStdout` turned on instead of writing a file to the workspace.
* Git.getVersion: Updated to remove `println` or move them to `debugPrint`.
* GitHubApi.githubRequestWrapper: Add missing `withCredentials` block as well as set the `Authorization` header based on credentials.
* GitHubApi.getPullRequests: Moved to use GraphQL instead of the v3 Rest API.
* plNotify: Moved to a closure so instead of calling like this: `plNotify(...)` it must now be called like this `plNotify { ... }`.

## 0.1.0

### Added

* Ability to load local and remote workflows.
* `debugPrint` method with the ability to control whether to print or not by level.
* Error handling when executing at various levels.
* Text manipulation including replacements using mustache templating style.
* Version generation by looking at existing tags on the repo and generating a semantic compatible version number.
* Various helper for setting environment variables for Git information.
* Simplified execution using `plWorkflow` closure.
