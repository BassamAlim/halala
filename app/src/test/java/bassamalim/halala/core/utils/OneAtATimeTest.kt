package bassamalim.halala.core.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OneAtATimeTest {

    @Test
    fun `a second tap while saving does nothing, and after a save that finished none does`() = runTest(UnconfinedTestDispatcher()) {
        val guard = OneAtATime()
        val writing = CompletableDeferred<Unit>()
        var saves = 0

        guard.launch(this) { saves++; writing.await(); true }
        guard.launch(this) { saves++; true }
        writing.complete(Unit)
        guard.launch(this) { saves++; true }

        assertEquals(1, saves)
    }

    @Test
    fun `after a save with problems the next tap goes through`() = runTest(UnconfinedTestDispatcher()) {
        val guard = OneAtATime()
        var saves = 0

        guard.launch(this) { saves++; false }
        guard.launch(this) { saves++; true }

        assertEquals(2, saves)
    }
}
