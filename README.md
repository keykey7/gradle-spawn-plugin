# Gradle Spawn Plugin
A plugin for the Gradle build system that allows spawning, detaching and terminating processes on unix systems.

[![Build Status](https://travis-ci.com/keykey7/gradle-spawn-plugin.svg?branch=master)](https://travis-ci.com/keykey7/gradle-spawn-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellowgreen.svg)](LICENSE)

## Usage

### Applying the plugin
To use the Spawn plugin, include the following in your build script:
```groovy
plugins {
    id 'ch.kk7.spawn'
}
```

## Tasks
The plugin introduces two task types to your gradle build, but doesn't add any explicit tasks itself.

### Spawn Task
The Spawn Task will bring a given command in a running state and put it into background. 
If configured correctly, it will not spawn a second instance but can still determine if the previously running instance died. 
A simple setup looks like:
```groovy
task ping(type: SpawnTask) {
    command "ping localhost"
}
```

Spawn Task extends the Kill Task, since it might need to kill before launching a new process in case its configuration changed.
A full example:

```groovy
task processStarted(type: SpawnTask) {
    // mandatory command to be spawned (List<String>)
    // alternative: command "commandToRun with arguments"
    commandLine "commandToRun", "with", "arguments"
    
    // special environment variables for the new process (Map)
    environment ENV_KEY: "ENV_VALUE"
    
    // regular expression to await on the processes stdout/stderr before assuming it is successfully started
    // this let's you block the task until a service is ready and put it in background at this point
    waitFor "something within the [Ll]ogs?"
    
    // maximum wait time in milliseconds for the `waitFor` expression to match
    waitForTimeout 30000
    
    // Alternate location of PID (and instance) file
    pidFile "${buildDir}/spawn/${name}.pid"
    
    workingDir "another/process/base/dir"
    
    // milliseconds before killing the process the hard way (kill -9)
    killTimeout 5000
    
    // recommended way of killing the process if the PID was lost (like `killall` or `pkill`). 
    // Try to avoid sideffects as `killall java` might not be a good idea.
    killallCommandLine "pkill", "-f", "somebinary .* arg2"
    killallCommandLine "bash", "-c", "kill -9 \$(lsof -t -i:8080 -sTCP:LISTEN)"
    
    // use normal gradle inputs to let it fail the up-to-date checks
    // which triggers a kill+relaunch of the process
    inputs.file = "src/main/resources/config.properties"
}
// test.dependsOn processStarted
```

### Kill Task
Whereas the `SpawnTask` will (kill and) launch a process, the `KillTask` will only remove it.
Usefull as part of a cleanup step. A subset of the methods of the spawn task are supported.
```groovy
task processStopped(type: SpawnTask) {
    // inherits properties from a given SpawnTask
    kills mySpawnTask
    
    // alternatively use the previously explained...
    pidFile
    killTimeout
    killallCommandLine
}
// test.finalizedBy processStopped
```

Killing a process has mutltiple steps:
 * if the pid file exists: `kill $pid`
 * if the pid file exists after `$killTimeout`ms elapsed: `kill -9 $pid` and remove the pid file
 * if `killallCommandLine` is defined, run it

### Limitations
The plugin requires the native `kill` command on the system and access to the hidden pid filed of the JVM (Oracle or OpenJDK).
