package eu.darken.sdmse.systemcleaner.core.filter.specific

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.mockDataStoreValue

class TombstonesFilterFactoryTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = TombstonesFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()

        mockNegative(Type.DATA, "tombstones", Flags.DIR)
        mockPositive(Type.DATA, "tombstones/$rngString", Flags.FILE)

        confirm(create())
    }

    @Test fun `only with root`() = runTest {
        TombstonesFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterTombstonesEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(true)
            }
        ).isEnabled() shouldBe true

        TombstonesFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterTombstonesEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(false)
            }
        ).isEnabled() shouldBe false
    }
}
