package app.thermida.data

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLinksTest {
    @Test
    fun everyServiceExceptCustomHasAnHttpsKeyPage() {
        Provider.entries.forEach { provider ->
            val url = provider.keyUrl
            if (provider == Provider.CUSTOM) {
                assertNull(url)
            } else {
                assertTrue(provider.name, url!!.startsWith("https://"))
            }
        }
    }
}
