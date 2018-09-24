# Gradle Spawn Plugin
A plugin for the Gradle build system that allows spawning, detaching and terminating processes on unix systems.

[![Build Status](https://travis-ci.com/keykey7/gradle-spawn-plugin.svg?branch=master)](https://travis-ci.com/keykey7/gradle-spawn-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellowgreen.svg)](LICENSE)
[![release](https://api.bintray.com/packages/kk7/mvn-release/gradle-spawn-plugin/images/download.svg) ](https://bintray.com/kk7/mvn-release/gradle-spawn-plugin/_latestVersion)

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
    // mandatory command to be spawned
    // alternative: command "commandToRun with arguments"
    commandLine "commandToRun", "with", "arguments"
    
    // special environment variables for the new process
    environment ENV_KEY: "ENV_VALUE"
    
    // regular expression to await on the processes stdout/stderr before assuming it is successfully started
    // this let's you block the task until a service is ready and put it in background at this point
    waitFor "Listening on port [0-9]+"
    
    // maximum wait time in milliseconds for the `waitFor` expression to match
    waitForTimeout 30000
    
    // Alternate location of PID (and instance) files
    pidFile "${buildDir}/spawn/${name}.pid"
    
    workingDir "another/process/base/dir"
    
    // milliseconds before killing the process the hard way (kill -9)
    killTimeout 5000
    
    // recommended way of killing the process if the PID was lost (like `killall` or `pkill`). 
    // Try to avoid sideffects as `killall java` might not be a good idea.
    killallCommandLine "pkill", "-f", "somebinary .* arg2"
    killallCommandLine "bash", "-c", "kill -9 \$(lsof -t -i:8080 -sTCP:LISTEN)"
    
    // stdout and stderr are redirected into this file
    stdOutFile "${pidFile}.out"
    
    // use standard gradle inputs to let it fail the up-to-date checks
    // which triggers a kill+relaunch of the process
    inputs.file = "src/main/resources/config.properties"
}
// test.dependsOn processStarted
```

Starting a process performs the following steps:
 * if no PID file exists, no matching process is running or any input changed → not up-to-date
 * if not up-to-date: kill running processes (see `KillTask`)
 * start a new process and listen on stdout/stderr until the `waitFor` expression matches

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

Killing a process performs the following steps:
 * if the pid file exists: `kill $pid`
 * if the pid file exists after `$killTimeout`ms elapsed: `kill -9 $pid` and remove the pid file
 * if `killallCommandLine` is defined, run it

### Limitations
The plugin requires the native `kill` command on the system and access to the hidden pid filed of the JVM (Oracle or OpenJDK).
