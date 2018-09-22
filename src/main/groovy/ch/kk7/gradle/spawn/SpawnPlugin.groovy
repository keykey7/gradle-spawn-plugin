package ch.kk7.gradle.spawn

import org.gradle.api.Plugin
import org.gradle.api.Project

class SpawnPlugin implements Plugin<Project> {
	@Override
	void apply(Project project) {
		// no magic, just make the tasks known
		project.with {
			ext.SpawnTask = SpawnTask
			ext.KillTask = KillTask
		}
	}
}
