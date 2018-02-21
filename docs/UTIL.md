# com.concur.Util

## dateFromString(String, String)

> default format is to match how a Git tag date is formatted

| Type   | Name       | Default                |
|:-------|:-----------|:-----------------------|
| String | dateString |                        |
| String | format     | 'yyyy-MM-dd HH:mm:ss Z |

### Example 1

```groovy
def dateStr = sh returnStdout: true, script: "git log --pretty="format:%ci" $(git tag --sort -v:refname) | head -1"
println new com.concur.Util().dateFromString(dateStr)
// Sun Jan 07 01:37:49 GMT 2018
```

### Example 2

```groovy
println new com.concur.Util().dateFromString('01-02-2018', 'MM-dd-yyyy')
// Tue Jan 02 00:00:00 GMT 2018
```

## parseJSON(String)

> Parses the provided string as if it is YAML

| Type   | Name          | Default   |
|:-------|:--------------|:----------|
| String | stringContent |           |

### Example 1

```groovy
println new com.concur.Util().parseJSON('{"content": "JSON content"}')
// {content=JSON content}
```

### Example 2

```groovy
println new com.concur.Util().parseJSON(readFile('results.json'))
// {content=JSON content}
```

## toJSON(Object)

> Convert the provided content into a valid JSON string

| Type   | Name    | Default   |
|:-------|:--------|:----------|
| Object | content |           |

### Example 1

```groovy
println new com.concur.Util().toJSON(['key1': 'value1', 'key2': 'value2'])
// {"key1":"value1","key2":"value2"}
```

### Example 2

```groovy
println new com.concur.Util().toJSON(['item1', 'item2', 'item3', 'item4'])
// ["item1","item2","item3","item4"]
```

### Example 3

```groovy
println new com.concur.Util().toJSON('Valid JSON string \'""')
// "Valid JSON string '\"\""
```

## parseYAML(String)

> Parses the provided string as if it is YAML

| Type   | Name          | Default   |
|:-------|:--------------|:----------|
| String | stringContent |           |

### Example 1

```groovy
println new com.concur.Util().parseYAML('''
content: |
  multiline string in YAML
'''.stripIndent())
// {content=multiline string in YAML}
```

### Example 2

```groovy
println new com.concur.Util().parseYAML(readFile('pipelines.yml'))
// {pipelines={tools={git={...}}}}
```

## parseChangelog(String, String)

> Loads the changelog file specified and gathers the release information. More information about good ways to format changelogs can be found at [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)

__**Changelog must have consistent usage of headers and follow markdown standards.**__

| Type   | Name          | Default      |
|:-------|:--------------|:-------------|
| String | changelogFile | CHANGELOG.md |
| String | releaseHeader | ##           |

### Example 1

```groovy
println new com.concur.Util().parseChangelog()
// {0.2.0=
// ### Added....
```

### Example 2

```groovy
println new com.concur.Util().parseChangelog('docs/CHANGELOG.md', '# ')
// {0.2.0=
// ### Added....
```

## installGoPkg(String, String)

> Checks if a Go binary is installed and if not install it using provided information.

| Type   | Name   | Default   |
|:-------|:-------|:----------|
| String | cmd    |           |
| String | repo   |           |

### Example 1

```groovy
new com.concur.Util().installGoPkg('glide', 'github.com/Masterminds/glide')
```

### Example 2

```groovy
new com.concur.Util().installGoPkg('dep', 'github.com/golang/dep')
```

## binAvailable(String)

> Use which command to determine if a binary/command is available on the linux system

| Type   | Name   | Default   |
|:-------|:-------|:----------|
| String | bin    |           |

### Example 1

```groovy
println new com.concur.Util().binAvailable('python')
// true
```

### Example 2

```groovy
println new com.concur.Util().binAvailable('go')
// false
```

## kebab(String)

> convert a string to lower-case kebab-case

| Type   | Name   | Default   |
|:-------|:-------|:----------|
| String | s      |           |

### Example 1

```groovy
println new com.concur.Util().kebab('Jenkins Workflow Libraries')
// jenkins-workflow-libraries
```

### Example 2

```groovy
println new com.concur.Util().kebab('alpha_release-0.2.3')
// alpha-release-0-2-3
```

## replaceLast(String, String, String)

> Replace the last instance of a provided regex with a provided replacement.

| Type   | Name        | Default   |
|:-------|:------------|:----------|
| String | text        |           |
| String | regex       |           |
| String | replacement |           |

### Example 1

```groovy
new com.concur.Util().replaceLast('0.1.0.32984', /\./, '-')
// 0.1.0-32984
```

## mustacheReplaceAll(String, Map)

> Replace text in a provided String that contains mustache style templates.

| Type   | Name           | Default   |
|:-------|:---------------|:----------|
| String | str            |           |
| Map    | replaceOptions | [:]       |

### Example 1

```groovy
println new com.concur.Util().mustacheReplaceAll('Hello {{ git_owner }}')
// Hello Concur
```

### Example 2

```groovy
println new com.concur.Util().mustacheReplaceAll('{{ non_standard }} | {{ git_repo }}', ['non_standard': 'This is not provided as an environment variable'])
// This is not provided as an environment variable | jenkins-yml-workflowLibs
```