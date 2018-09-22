package ch.kk7.gradle.spawn

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpawnTaskTest {

	/**
	 * a thread who will append a count to a file. spawn tests can {@code tailf} this file and are sure
	 * that the process stops at the end of the test even if the test fails
	 */
	private static class FileCountingThread extends Thread {
		volatile stop = false
		public counter = 0
		public final File testfile

		FileCountingThread(File rootDir) {
			testfile = new File(rootDir, "testfile.txt")
			testfile.write "start"
		}

		@Override
		void run() {
			while (!stop) {
				testfile << "\ndumbadi ${counter++} dumbada"
				sleep 100
			}
			testfile.delete() // to kill the maybe still running tail commands
		}
	}

	private String command = "tail -f testfile.txt"

	@Rule
	public TemporaryFolder testProjectDir = new TemporaryFolder()

	private FileCountingThread fileCounter

	@Before
	void startFileCountingThread() {
		fileCounter = new FileCountingThread(testProjectDir.root)
		fileCounter.start()
	}

	@After
	void killFileCountingThread() {
		fileCounter.stop = true
		fileCounter.join()
	}

	private GradleRunner run(String gradleTask) {
		String gradleFile = """
			// generated
			plugins {
				id "ch.kk7.spawn"
			}
			${gradleTask}"""
		testProjectDir.newFile("build.gradle").write gradleFile
		println "${gradleFile}"
		return run()
	}

	private GradleRunner run() {
		return GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(testProjectDir.root)
	}

	@Test
	void canSpawnIfTerminatesImmediately() {
		def result = run("""
			task sleep1Start(type: SpawnTask) {
				command "sleep 1"
			}""").withArguments("sleep1Start").build()
		assert result.task(":sleep1Start").outcome == TaskOutcome.SUCCESS
	}

	@Test
	void waitForRunningIntoTimeout() {
		def result = run("""
			task startTailTimeout(type: SpawnTask) {
				command "${command}"
				waitFor "willNeverAppear"
				waitForTimeout 1000
			}""").withArguments("startTailTimeout").buildAndFail()
		assert result.task(":startTailTimeout").outcome == TaskOutcome.FAILED
		assert result.output.contains("dumbadi") // some information about what actually was printed to stdout instead
	}

	@Test
	void waitForWithoutNewline() {
		File testFile = testProjectDir.newFile()
		testFile.text = "line1\nno newline 1337 at the end"
		run("""
			task startTail(type: SpawnTask) {
				command "tail -f ${testFile}"
				waitFor "1337"
			}""").withArguments("startTail").build()
	}

	@Test
	void upToDateIfAlreadyRunning() {
		def result = run("""
			task startTail(type: SpawnTask) {
				command "${command}"
				waitFor "dumbadi [0-9]+0" // matches once every second
			}""").withArguments("startTail").build()
		assert result.task(":startTail").outcome == TaskOutcome.SUCCESS
		// tail is still running now

		def result2 = run().withArguments("startTail").build()
		assert result2.task(":startTail").outcome == TaskOutcome.UP_TO_DATE
	}

	@Test
	void restartedIfDead() {
		def result = run("""
			task startTail(type: SpawnTask) {
				command "${command}"
			}
			task killTail(type: KillTask) {
				dependsOn startTail
				kills startTail
			}""").withArguments("killTail").build()
		assert result.task(":startTail").outcome == TaskOutcome.SUCCESS
		assert result.task(":killTail").outcome == TaskOutcome.SUCCESS

		def result2 = run().withArguments("startTail").build()
		assert new File(testProjectDir.root, "build").exists() // still there
		assert result2.task(":startTail").outcome == TaskOutcome.SUCCESS // explicitly not UP_TO_DATE
	}

	@Test
	void forceRestartedIfPidFileGone() {
		String killallFake = "killAllWasCalled123"
		run("""
			task startTail(type: SpawnTask) {
				command "${command}"
				killallCommandLine "echo", "${killallFake}" // simulates our killall xxx command
			}""").withArguments("startTail").build()
		File pidFile = new File(testProjectDir.root, "build/spawn/startTail.pid")
		assert pidFile.exists()
		pidFile.delete()
		def result2 = run().withArguments("startTail").build()
		assert result2.task(":startTail").outcome == TaskOutcome.SUCCESS // explicitly not UP_TO_DATE
		assert result2.output.contains(killallFake)
	}

	@Test
	void restartedIfConfigChanged() {
		File configFile = testProjectDir.newFile()
		configFile.text = "config for first run"
		run("""
			task startTail(type: SpawnTask) {
				command "${command}"
				inputs.file "${configFile.name}"
			}""").withArguments("startTail").build()

		configFile.text = "config for SECOND run"
		def result2 = run().withArguments("startTail").build()
		assert result2.task(":startTail").outcome == TaskOutcome.SUCCESS // explicitly not UP_TO_DATE
	}

	@Test
	void specialPidLocation() {
		String pidFile = "somenewFile.pid"
		run("""
			task startTail(type: SpawnTask) {
				command "${command}"
				pidFile "${pidFile}"
				waitFor "dumbadi"
			}""").withArguments("startTail").build()
		assert new File(testProjectDir.root, pidFile).exists()
	}

	@Test
	void specialWorkingDirLocation() {
		run("""
			task startTail(type: SpawnTask) {
				command "${command}"
				workingDir "${testProjectDir.newFolder()}"
			}""").withArguments("startTail").build()
		assert new File(testProjectDir.root, "build/spawn/startTail.pid").exists()
	}

	@Test
	void dieIfInvalidCommand() {
		run("""
			task startTail(type: SpawnTask) {
				command "nonexistentCmd"
			}""").withArguments("startTail").buildAndFail()
	}

	@Test
	void dieIfKillWithoutSpawn() {
		run("""
			task killSomething(type: KillTask)
			""").withArguments("killSomething").buildAndFail()
	}
}
