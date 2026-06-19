package com.neostride.server.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class AdminConsoleArchitectureBoundaryTest {
	private final JavaClasses productionClasses = new ClassFileImporter()
			.withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages("com.neostride.server");

	@Test
	void adminOpsAndDevtoolsDoNotDependOnForeignRepositories() {
		noClasses()
				.that().resideInAnyPackage(
						"com.neostride.server.admin..",
						"com.neostride.server.ops..",
						"com.neostride.server.devtools.."
				)
				.should().dependOnClassesThat().resideInAnyPackage(
						"com.neostride.server.auth.repository..",
						"com.neostride.server.community.repository..",
						"com.neostride.server.coaching.repository..",
						"com.neostride.server.crew.repository..",
						"com.neostride.server.notification.repository..",
						"com.neostride.server.running.repository.."
				)
				.check(productionClasses);
	}

	@Test
	void authRepositoryDoesNotDependOnAdminOpsOrDevtools() {
		noClasses()
				.that().resideInAnyPackage("com.neostride.server.auth.repository..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"com.neostride.server.admin..",
						"com.neostride.server.ops..",
						"com.neostride.server.devtools.."
				)
				.check(productionClasses);
	}
}
