package ch.kk7.gradle.spawn

import org.gradle.api.GradleException
import org.gradle.api.tasks.*

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A task which will spawn and detach a new standalone process. Will check its inputs (config files, env vars,...)
 * to determine if the process should be (re-)started. After launching the process it will wait for an expression to
 * appear on its stdout/stderr once the process is ready. Or timeout/die if this is not possible.
 *
 * based on the idea of https://github.com/marc0der/gradle-spawn-plugin/
 */
class SpawnTask extends KillTask {
	@Internal
	List<String> commandLine

	@Optional
	@Input
	Map<String, String> environment = new HashMap<>()

	/**
	 * after the process is spawned wait until this regex appears on stdout/stderr
	 * not an @Input: simply a fancy sleep
	 */
	@Internal
	String waitFor

	/** time to wait in milliseconds for the #waitFor expression to appear, abort gradle otherwise (but not the process) */
	@Internal
	long waitForTimeout = 30000

	/** where to write stdout/stderr to during startup phase */
	@Internal
	File stdOutFile

	// spawn task needs an output, kill task does not
	@OutputFile
	File getPidFile() {
		super.getPidFile()
	}

	@Internal
	File getStdoutFile() {
		return stdOutFile ?: project.file("${getPidFile()}.out")
	}

	/**
	 * @return a temp file which will remain static IF the process remains running
	 */
	@InputFile
	File getAttemptIdFile() {
		File pidFile = getPidFile()
		File pidInstFile = project.file("${pidFile}.inst")
		if (pidFile.exists() && pidInstFile.exists()) {
			int pid = Integer.parseInt(pidFile.text)
			if (kill("-0", "${pid}")) {
				logger.info "process with pid ${pid} is already running..."
				return pidInstFile // no start required
			}
			logger.warn "removing pid file since pid ${pid} is gone"
			pidFile.delete()
		} else {
			logger.info "no pid file yet"
		}
		pidInstFile.getParentFile().mkdirs()
		pidInstFile.write UUID.randomUUID().toString() // new content to fail up-to-date check
		return pidInstFile
	}

	void commandLine(String... arguments) {
		this.commandLine = arguments
	}

	void command(String command) {
		this.commandLine = command.split(" ")
	}

	void configDir(String configDir) {
		this.configDir = project.file(configDir)
	}

	void configFile(String... files) {
		files.each { file ->
			configFiles << project.file(file)
		}
	}

	/**
	 * only called when there is not already a matching process running (not up-to-date), thus we will restart
	 */
	@TaskAction
	void execRestart() {
		if (!commandLine) {
			// late validation, such that you can build this dynamically
			throw new GradleException("Ensure that mandatory field commandLine is set")
		}
		execKill()
		Process process = startProcess()
		int pid = extractPidFromProcess(process)
		getPidFile().text = pid
		logger.info "started process with PID ${pid}"
		waitFor(process)
	}

	private Process startProcess() {
		logger.info "starting:${workingDir}\$ ${commandLine}"
		def builder = new ProcessBuilder(commandLine)
		builder.redirectErrorStream(true)
		builder.environment().putAll(environment)
		builder.directory(workingDir)
		builder.start()
	}

	/**
	 * block this task until the process is sucessfully started (or timed out or died)
	 * @param process to await
	 */
	private void waitFor(Process process) {
		if (!waitFor) {
			return
		}
		def reader = new InputStreamReader(process.getInputStream())
		def stdOutFile = getStdoutFile()
		TeeReader teeReader = new TeeReader(reader, new FileWriter(stdOutFile))
		Scanner scanner = new Scanner(teeReader)
		logger.info "waiting for '${waitFor}'..."
		String result
		try {
			result = CompletableFuture.supplyAsync({ scanner.findWithinHorizon(waitFor, 0) })
					.get(waitForTimeout, TimeUnit.MILLISECONDS)
		}
		catch (TimeoutException e) {
			logger.warn(stdOutFile.text)
			logger.warn("[sip after ${waitForTimeout}ms]")
			throw new GradleException("process timed out after ${waitForTimeout}ms before reaching '${waitFor}'", e)
		}
		if (result == null) {
			logger.warn(stdOutFile.text)
			throw new GradleException("process ended before reaching '${waitFor}'")
		}
		logger.info(stdOutFile.text)
	}

	// workaround for: http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4244896
	private static int extractPidFromProcess(Process process) {
		def pidField = process.class.getDeclaredField('pid')
		pidField.accessible = true
		return pidField.getInt(process)
	}
}
