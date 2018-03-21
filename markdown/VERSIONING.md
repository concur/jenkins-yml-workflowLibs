# com.concur.Versioning

## getVersion(Map)

> Determine a version number based on the current latest tag in the repository. Will automatically increment the minor version and append a build version.
```yaml
pipelines:
  general:
    version:
      base: 1.0.0
      scheme: semantic
      versionImage: quay.io/reynn/docker-versioner:0.1.0
      pattern: "^.*.*"
```


| Type   | Name   | Default   |
|:-------|:-------|:----------|
| Map    | yml    |           |

### Example 1

```groovy
// Latest tag in the repo is 1.3.1 and it was tagged 5 hours ago
println new com.concur.Versioning().getVersion(yml)
// 1.4.0-0018000000
```

### Example 2

```groovy
// New repo with no tags, repository was created 1 hour ago
println new com.concur.Versioning().getVersion(yml)
// 0.1.0-0003600000
```

### Example 3

```groovy
// No tags in repo, override default version, created 18 days ago
println new com.concur.Versioning().getVersion(yml)
// 3.7.0-1555200000
```