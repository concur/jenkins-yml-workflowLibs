# Jenkins-YML-WorkflowLibs changes by release

## 0.1.0

### New Features

Initial release includes the following

* Ability to load local and remote workflows.
* `debugPrint` method with the ability to control whether to print or not by level.
* Error handling when executing at various levels.
* Text manipulation including replacements using mustache templating style.
* Version generation by looking at existing tags on the repo and generating a semantic compatible version number.
* Various helper for setting environment variables for Git information.
* Simplified execution using `plWorkflow` closure.
