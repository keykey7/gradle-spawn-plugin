# Gradle Spawn Plugin
A plugin for the Gradle build system that allows spawning, detaching and terminating processes on unix systems.

## Usage

### Applying the plugin
To use the Spawn plugin, include the following in your build script:
```groovy
plugins {
    id 'ch.kk7.spawn'
}
```

### Prerequisites
The plugin requires the native `kill` command on the system and access to the hidden pid filed of the JVM (Oracle or OpenJDK).

### Alternatives
There are many better alternatives to using this plugin, like the `bmuschko/gradle-cargo-plugin` if your are dealing 
with java containers. Or use docker images wherever possible. Consider spawning native processes as a last resort.

## Tasks
The plugin introduces two task types to your gradle build.

### Spawn Task
The Spawn Task will bring a given command in a running state. If configured correctly, it will not spawn a second instance but can
still determine if the previously running instance died.

Spawn Task extends the Kill Task, since it might need to kill before launching a new process in case its configuration changed.

property    | Gradle default          | Type   | Description
------------|-------------------------|--------|-----------
commandLine | - (mandatory)           | List\<String> | command to be spawned
command     | -                       | String | alternative: space separated command
environment | -                       | Map    | special environment variables
waitFor     | -                       | Regex  | regular expression to await on the processes stdout/stderr before assuming it is successfully started
waitForTimeout | 30000                | long   | milliseconds to wait for the `waitFor` expression at most
pidFile*       | ${workingDir}/spawn.pid | File | Alternate location of PID (and instance) file
workingDir*    | ${projectDir}           | File | base directory, mostly used in Spawn Task
killTimeout*   | 5000                    | long | milliseconds before killing the process the hard way
killallCommandLine* | -                  | List\<String> | recommended way of killing the process if the PID was lost (like `killall` or `pkill`). Try to avoid sideffects as killall java might not be a good idea.

Sample usage:
```groovy
task processStarted(type: SpawnTask) {
	command "./somebinary arg1 arg2"
	workingDir "build/native"
	waitFor "Server started successfully"
	killallCommandLine "pkill", "-f", "somebinary .* arg2"
}
acceptanceTest.dependsOn processStarted
```

Up-to-date checks are honored in a sense that it will restart the process if an input changed:
```groovy
task execWithConfig(type: SpawnTask) {
    inputs.dir "path/to/config"
    command "somebinary --with-config"
}
```

### Kill Task
The Kill Task will terminate a process previously started by the Spawn Task. It provides a subset of the Spawn Task properties 
(marked with *).

Sample usage:
```groovy
task processStop(type: KillTask) {
	workingDir "build/native"
	killallCommandLine "pkill", "-f", "tailf testfile.*.txt"
}
acceptanceTest.finalizedBy processStop
```

Killing a process has mutltiple steps:
 * if the pid file exists: `kill $pid`
 * after `$killTimeout`ms: `kill -9 $pid` and remove the pid file
 * if `killallCommandLine` is defined, run it
