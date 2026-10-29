package com.jarvismini.convo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun msg(id: Long, to: String, from: String = ADMIN_ID, bid: String? = null, read: Long? = null, text: String = "hi") =
    ConvoMessage(id, "u$id", from, to, "chat", text, bid, id * 1000L, read)

private val agents = listOf(
    ConvoAgent("claude1", "claude1", "agent", 1_000L, true),
    ConvoAgent("claude2", "claude2", "agent", null, false),
    ConvoAgent("sir", "sir", "admin", null, false),
)

class GroupingTest {
    @Test fun broadcastRowsCollapseToOneBubble() {
        val b = groupMessages(listOf(msg(1, "claude1", bid = "b1"), msg(2, "claude2", bid = "b1"), msg(3, "claude1")))
        assertEquals(2, b.size)
        assertTrue(b[0].isBroadcast)
        assertEquals(listOf("claude1", "claude2"), b[0].receipts.map { it.agentId })
        assertFalse(b[1].isBroadcast)
    }

    @Test fun oneCheckDeliveredTwoChecksRead() {
        val partial = groupMessages(listOf(msg(1, "claude1", bid = "b", read = 5), msg(2, "claude2", bid = "b")))[0]
        assertEquals(ReceiptState.Delivered, partial.state)
        assertEquals(listOf("claude1"), partial.readBy)
        assertEquals(listOf("claude2"), partial.unreadBy)
        val all = groupMessages(listOf(msg(1, "claude1", bid = "b", read = 5), msg(2, "claude2", bid = "b", read = 6)))[0]
        assertEquals(ReceiptState.Read, all.state)
    }

    @Test fun filters() {
        val bubbles = groupMessages(listOf(msg(1, "claude1", read = 3), msg(2, "claude2"), msg(3, "sir", from = "claude1")))
        assertEquals(3, bubbles.filtered(ConvoFilter.All).size)
        assertEquals(listOf(2L, 3L), bubbles.filtered(ConvoFilter.Unread).map { it.sortId })
        assertEquals(listOf(3L), bubbles.filtered(ConvoFilter.FromAgents).map { it.sortId })
        assertEquals(listOf(1L, 2L), bubbles.filtered(ConvoFilter.FromMe).map { it.sortId })
    }

    @Test fun lastSeenFormatting() {
        assertEquals("never seen", formatLastSeen(null, 0))
        assertEquals("just now", formatLastSeen(0, 30_000))
        assertEquals("5m ago", formatLastSeen(0, 300_000))
        assertEquals("2h ago", formatLastSeen(0, 7_200_000))
    }

    @Test fun messageableExcludesAdmin() {
        assertEquals(listOf("claude1", "claude2"), agents.messageable().map { it.id })
    }
}

class JsonTest {
    @Test fun parsesAgentsAndPage() {
        val a = ConvoJson.parseAgents("""{"agents":[{"agent_id":"claude1","display_name":"c1","role":"agent","last_seen":5,"online":true},{"agent_id":"x","last_seen":null}]}""")
        assertEquals(2, a.size)
        assertTrue(a[0].online)
        assertNull(a[1].lastSeen)
        val p = ConvoJson.parsePage("""{"messages":[{"id":7,"message_uuid":"u","sender_id":"sir","recipient_id":"claude1","message_type":"chat","payload":{"text":"yo","broadcast_id":"b"},"created_at":9,"read_at":null}],"next_cursor":7,"first_id":7}""")
        assertEquals("yo", p.messages[0].text)
        assertEquals("b", p.messages[0].broadcastId)
        assertNull(p.messages[0].readAt)
        assertEquals(7L, p.nextCursor)
    }

    @Test fun broadcastBody() {
        assertTrue(ConvoJson.broadcastBody("a", null).contains("\"recipients\":\"all\""))
        assertTrue(ConvoJson.broadcastBody("a", listOf("claude1")).contains("\"recipients\":[\"claude1\"]"))
    }

    @Test fun trailingSlashAndBlankUrl() {
        assertEquals("http://h:1", normalizeBaseUrl(" http://h:1/ "))
        assertEquals(DEFAULT_CONVO_BASE_URL, normalizeBaseUrl(""))
    }
}

class ViewModelTest {
    private fun vm(repo: ConvoRepository, token: String = "t") =
        ConvoViewModel(repo, InMemoryConvoSettings(token = token), clock = { 123L })

    @Test fun refreshLoadsAgentsAndGroupedTimeline() = runBlocking {
        val repo = FakeConvoRepository(agents, listOf(msg(1, "claude1", bid = "b"), msg(2, "claude2", bid = "b")))
        val vm = vm(repo)
        vm.refresh()
        assertEquals(3, vm.state.value.agents.size)
        assertEquals(1, vm.state.value.bubbles.size)
        assertNull(vm.state.value.error)
    }

    @Test fun readReceiptUpdatesOnNextRefresh() = runBlocking {
        val repo = FakeConvoRepository(agents, listOf(msg(1, "claude1")))
        val vm = vm(repo)
        vm.refresh()
        assertEquals(ReceiptState.Delivered, vm.state.value.bubbles[0].state)
        repo.markRead(1, 50)
        vm.refresh()
        assertEquals(ReceiptState.Read, vm.state.value.bubbles[0].state)
    }

    @Test fun sendToAllAndToSubset() = runBlocking {
        val repo = FakeConvoRepository(agents)
        val vm = vm(repo)
        vm.refresh()
        vm.onDraft("hello")
        vm.send()
        assertNull(repo.sent[0].second)
        assertEquals("", vm.state.value.draft)
        assertEquals(1, vm.state.value.bubbles.size)
        vm.toggleRecipient("claude2")
        vm.onDraft("only you")
        vm.send()
        assertEquals(listOf("claude2"), repo.sent[1].second)
    }

    @Test fun serverDownShowsErrorWithoutCrash() = runBlocking {
        val repo = FakeConvoRepository(agents)
        repo.failing = ConvoException("Cannot reach Convo server")
        val vm = vm(repo)
        vm.refresh()
        assertEquals("Cannot reach Convo server", vm.state.value.error)
        vm.onDraft("x")
        vm.send()
        assertNotNull(vm.state.value.error)
        assertEquals("x", vm.state.value.draft)
    }

    @Test fun blankDraftIsNotSent() = runBlocking {
        val repo = FakeConvoRepository(agents)
        val vm = vm(repo)
        vm.onDraft("   ")
        vm.send()
        assertTrue(repo.sent.isEmpty())
    }

    @Test fun blankTokenKeepsSavedAndUrlChangeResets() = runBlocking {
        val repo = FakeConvoRepository(agents, listOf(msg(1, "claude1")))
        val settings = InMemoryConvoSettings(token = "keep")
        val vm = ConvoViewModel(repo, settings, clock = { 1L })
        vm.refresh()
        vm.applySettings("http://other:9", "")
        assertEquals("keep", settings.token)
        assertEquals("http://other:9", settings.baseUrl)
        assertTrue(vm.state.value.hasToken)
    }
}
