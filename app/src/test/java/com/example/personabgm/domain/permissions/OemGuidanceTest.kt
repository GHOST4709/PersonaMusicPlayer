package com.example.personabgm.domain.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemGuidanceTest {
    private fun brand(m: String?) = OemGuidance.forManufacturer(m)?.brand

    @Test fun knownBrandsAreRecognisedRegardlessOfCase() {
        assertEquals("Samsung", brand("samsung"))
        assertEquals("Samsung", brand("SAMSUNG"))
        assertEquals("Samsung", brand("  Samsung Electronics "))
        assertEquals("Xiaomi", brand("Xiaomi"))
        assertEquals("Xiaomi", brand("Redmi"))
        assertEquals("Xiaomi", brand("POCO"))
        assertEquals("OnePlus", brand("OnePlus"))
        assertEquals("Oppo", brand("OPPO"))
        assertEquals("Realme", brand("realme"))
        assertEquals("Vivo", brand("vivo"))
        assertEquals("Huawei", brand("HUAWEI"))
        assertEquals("Honor", brand("HONOR"))
    }

    @Test fun phonesWithoutAggressiveBatteryManagersGetNoTip() {
        assertNull(OemGuidance.forManufacturer("Google"))
        assertNull(OemGuidance.forManufacturer("motorola"))
        assertNull(OemGuidance.forManufacturer("Sony"))
        assertNull(OemGuidance.forManufacturer(""))
        assertNull(OemGuidance.forManufacturer(null))
    }

    @Test fun everyTipHasAdviceAndAnHttpsLink() {
        listOf("samsung", "xiaomi", "oneplus", "oppo", "realme", "vivo", "iqoo", "huawei", "honor").forEach {
            val tip = OemGuidance.forManufacturer(it)!!
            assertTrue("$it advice", tip.advice.isNotBlank())
            assertTrue("$it url", tip.moreInfoUrl.startsWith("https://dontkillmyapp.com/"))
        }
    }

    @Test fun linksUseTheBrandsOwnPage() {
        assertEquals("https://dontkillmyapp.com/samsung", OemGuidance.forManufacturer("samsung")!!.moreInfoUrl)
        assertEquals("https://dontkillmyapp.com/xiaomi", OemGuidance.forManufacturer("Redmi")!!.moreInfoUrl)
        assertEquals("https://dontkillmyapp.com/huawei", OemGuidance.forManufacturer("HUAWEI")!!.moreInfoUrl)
    }
}
