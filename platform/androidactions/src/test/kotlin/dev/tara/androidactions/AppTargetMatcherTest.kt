package dev.tara.androidactions

import dev.tara.domain.TargetOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTargetMatcherTest {
    @Test fun exactLabelWinsOverSubstringMatches() {
        val targets = listOf(TargetOption("example.camera", "Camera"), TargetOption("example.camerapro", "Camera Pro"))
        assertEquals(listOf(targets.first()), selectAppTargets(targets, "camera"))
    }

    @Test fun duplicateLabelsRemainAmbiguousRatherThanChoosingOne() {
        val targets = listOf(TargetOption("first.camera", "Camera"), TargetOption("second.camera", "Camera"))
        assertEquals(targets, selectAppTargets(targets, "Camera"))
    }

    @Test fun packageIdentifierCanResolveExactTarget() {
        val targets = listOf(TargetOption("example.camera", "Camera"), TargetOption("other.camera", "Camera"))
        assertEquals(listOf(targets.first()), selectAppTargets(targets, "example.camera"))
    }

    @Test fun duplicateLauncherActivitiesDoNotDuplicateTheSamePackage() {
        val target = TargetOption("example.camera", "Camera")
        assertEquals(listOf(target), selectAppTargets(listOf(target, target), "Camera"))
    }

    @Test fun unavailableOrDisabledApplicationsCannotBeResolved() {
        assertTrue(selectAppTargets(emptyList(), "Camera").isEmpty())
        assertTrue(selectAppTargets(listOf(TargetOption("example.mail", "Mail")), "Camera").isEmpty())
    }

    @Test fun blankQueryDoesNotReturnApplicationInventory() {
        assertTrue(selectAppTargets(listOf(TargetOption("example.camera", "Camera")), " ").isEmpty())
    }
}
