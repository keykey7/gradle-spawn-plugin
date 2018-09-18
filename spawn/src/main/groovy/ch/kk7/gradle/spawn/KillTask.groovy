package ch.kk7.gradle.spawn

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Kill a process defined by its PID file. Will always fire (never up-to-date).
 * Optionally accepts a killall command, which is usefull to clean up in case of clean reinstallation without prior kill of an
 * already running process.
 *
 * Note: many @Internal properties, because they only influence the killing behaviour, but not the end result. You are free
 * however to modify them.
 */
class KillTask extends DefaultTask {
	/** alternate location of the PID file */
	@Internal
	File pidFile = null

	/** working dir of this process, mainly used within the SpawnTask */
	@Input
	File workingDir = project.file('.')

	/** timeout in ms after which we will send the kill -9 */
	@Internal
	int killTimeout = 5000

	/**
	 * kill might not cover it if the pid file was removed or versions changes, therefore an additional killall option
	 * use it like: "pkill", "-f", "<regex mathing my super process>"
	 */
	@Internal
	List<String> killallCommandLine

	// NOT @OutputFile: kill task has no outputs
	File getPidFile() {
		return pidFile
	}

	void pidFile(String pidFile) {
		this.pidFile = project.file(pidFile)
	}

	void kills(String spawnTaskName) {
		kills(project.getTasksByName(spawnTaskName, false).first())
	}

	void kills(Task spawnTask) {
		pidFile = spawnTask.getPidFile()
	}

	void killallCommandLine(String... arguments) {
		this.killallCommandLine = arguments
	}

	void workingDir(String workingDir) {
		this.workingDir = project.file(workingDir)
	}

	@TaskAction
	void execKill() {
		if (pidFile == null) {
			throw new GradleException("Missing attribute 'pidFile': "+
					"Either specify a task to be killed using `${getName()}.kills(spawnTask)` "+
					"or a pid file directly via `${getName()}.pidFile(pathToPid)`")
		}
		killPid()
		killAll()
	}

	/**
	 * silent, non-crashing kill command
	 * @return true if return status code was 0
	 */
	boolean kill(String... killArgs) {
		def stdout = new ByteArrayOutputStream()
		def killResult = project.exec {
			ignoreExitValue true
			executable "kill"
			args killArgs
			standardOutput = stdout
			errorOutput = stdout
		}
		// don't confuse the dev with failure warnings which are expected
		logger.info "kill ${killArgs} out:" + new String(stdout.toByteArray())
		return killResult.getExitValue() == 0
	}

	private void killPid() {
		File pidFile = getPidFile()
		if (!pidFile.exists()) {
			logger.info("no PID file ${pidFile}, nothing to kill")
			return
		}
		int pid = Integer.parseInt(pidFile.text)
		long stopKillingAt = System.currentTimeMillis() + killTimeout
		while (System.currentTimeMillis() < stopKillingAt) {
			logger.info "killing PID ${pid}"
			if (!kill("${pid}")) {
				logger.info "PID ${pid} is no more"
				pidFile.delete()
				return
			}
			sleep 100
		}
		logger.warn("failed to kill PID ${pid} within ${killTimeout}ms, losing patience...")
		kill("-9", "${pid}")
		pidFile.delete()
	}

	private void killAll() {
		if (!killallCommandLine) {
			return
		}
		project.exec {
			ignoreExitValue true
			commandLine killallCommandLine
		}
	}
}
