package com.guanyji.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun `has five unique top level routes in product order`() {
        assertEquals(listOf("overview", "fish", "tanks", "care", "profile"), AppDestination.entries.map { it.route })
        assertEquals(AppDestination.entries.size, AppDestination.entries.map { it.route }.distinct().size)
    }

    @Test
    fun `detail routes stay selected under their owning top level destination`() {
        assertEquals(AppDestination.Overview, AppDestination.entries.firstOrNull { it.matchesRoute("overview") })
        assertEquals(AppDestination.Fish, AppDestination.entries.firstOrNull { it.matchesRoute("fish/detail/{fishId}") })
        assertEquals(AppDestination.Fish, AppDestination.entries.firstOrNull { it.matchesRoute("fish/records/{fishId}/{section}") })
        assertEquals(AppDestination.Tanks, AppDestination.entries.firstOrNull { it.matchesRoute("tank/detail/{tankId}") })
        assertEquals(AppDestination.Tanks, AppDestination.entries.firstOrNull { it.matchesRoute("tank/action/{tankId}/{action}") })
        assertEquals(AppDestination.Care, AppDestination.entries.firstOrNull { it.matchesRoute("care?view={view}") })
        assertEquals(AppDestination.Care, AppDestination.entries.firstOrNull { it.matchesRoute("care") })
        assertEquals(AppDestination.Profile, AppDestination.entries.firstOrNull { it.matchesRoute("statistics") })
        assertEquals(AppDestination.Profile, AppDestination.entries.firstOrNull { it.matchesRoute("settings?section={section}") })
        assertEquals(AppDestination.Profile, AppDestination.entries.firstOrNull { it.matchesRoute("about/{section}") })
        assertEquals(null, AppDestination.entries.firstOrNull { it.matchesRoute("feeding?tankId={tankId}&fishId={fishId}") })
    }

    @Test
    fun `every registered route has an explicit owner or documented standalone policy`() {
        assertEquals(registeredAppRoutes.size, registeredAppRoutes.distinct().size)
        registeredAppRoutes.forEach { route ->
            assertTrue("unregistered policy for $route", isKnownRoute(route))
            assertTrue("route must have owner or standalone policy: $route", topLevelOwner(route) != null || route == "feeding?tankId={tankId}&fishId={fishId}" || route == "navigation/unknown")
        }
        assertEquals(AppDestination.Fish, topLevelOwner("fish/create?tankId={tankId}"))
        assertEquals(AppDestination.Tanks, topLevelOwner("tank/action/{tankId}/change/{changeId}"))
        assertEquals(AppDestination.Care, topLevelOwner("care?view={view}"))
        assertEquals(AppDestination.Care, topLevelOwner("care"))
        assertEquals(null, topLevelOwner("feeding?tankId={tankId}&fishId={fishId}"))
        assertEquals(null, topLevelOwner("future/unregistered"))
    }

    @Test
    fun `bottom bar hides only on immersive detail pages`() {
        assertFalse(showsBottomBar("fish/detail/{fishId}"))
        assertFalse(showsBottomBar("tank/detail/{tankId}"))
        assertTrue(showsBottomBar("fish"))
        assertTrue(showsBottomBar("tanks"))
        assertFalse(showsBottomBar("fish/records/{fishId}/{section}"))
        assertFalse(showsBottomBar("tank/records/{tankId}/{section}"))
        assertFalse(showsBottomBar("feeding?tankId={tankId}&fishId={fishId}"))
        assertFalse(showsBottomBar("tank/action/{tankId}/{action}", "water"))
        assertFalse(showsBottomBar("tank/action/{tankId}/{action}", "change"))
        assertFalse(showsBottomBar("tank/action/{tankId}/change/{changeId}"))
        assertFalse(showsBottomBar("statistics"))
        assertTrue(showsBottomBar("tank/action/{tankId}/{action}", "task"))
        assertFalse(showsBottomBar(null))
    }

    @Test
    fun `unknown routes are rejected while the fallback stays standalone`() {
        assertTrue(isKnownRoute("navigation/unknown"))
        assertEquals(null, topLevelOwner("navigation/unknown"))
        assertTrue(!isKnownRoute("not-a-registered-route"))
    }

    @Test
    fun `optional route builders keep null values empty and encode identifiers`() {
        assertEquals("feeding", feedingRoute())
        assertEquals("feeding?tankId=tank%2F1&fishId=fish%3F2", feedingRoute("tank/1", "fish?2"))
        assertEquals("feeding?fishId=fish%3F2", feedingRoute(fishId = "fish?2"))
        assertEquals("fish/create", fishCreateRoute())
        assertEquals("fish/create?tankId=tank%2F1", fishCreateRoute("tank/1"))
        assertEquals("care", careRoute())
        assertEquals("care?view=history", careRoute("history"))
        assertEquals("fish/records/fish%2F1/growth", fishRecordsRoute("fish/1", "growth"))
    }

}
