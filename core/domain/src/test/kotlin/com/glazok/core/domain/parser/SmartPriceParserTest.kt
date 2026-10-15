package com.glazok.core.domain.parser

import com.glazok.core.domain.model.NormalizedRect
import com.glazok.core.domain.model.RawTextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmartPriceParserTest {

    private lateinit var parser: SmartPriceParser

    @Before
    fun setUp() {
        parser = SmartPriceParser()
    }

    @Test
    fun `test standard price integer rubles`() {
        val blocks = listOf(
            RawTextBlock(
                text = "Шоколад молочный 99 ₽",
                boundingBox = NormalizedRect(0.1f, 0.1f, 0.4f, 0.15f)
            )
        )

        val result = parser.parse(blocks)
        assertEquals(1, result.size)
        val tag = result[0]
        assertEquals(99.0, tag.finalPrice, 0.001)
        assertEquals(false, tag.isLoyaltyPrice)
        assertEquals("99 рублей", tag.toSpeechUtterance())
    }

    @Test
    fun `test price with kopecks`() {
        val blocks = listOf(
            RawTextBlock(
                text = "Молоко 3.2% 89.90 руб",
                boundingBox = NormalizedRect(0.2f, 0.3f, 0.6f, 0.36f)
            )
        )

        val result = parser.parse(blocks)
        assertEquals(1, result.size)
        val tag = result[0]
        assertEquals(89.90, tag.finalPrice, 0.001)
        assertEquals("89 руб 90 коп рублей", tag.toSpeechUtterance().substringBefore(" ").let { tag.toSpeechUtterance() })
        assertTrue(tag.toSpeechUtterance().contains("89 руб 90 коп"))
    }

    @Test
    fun `test loyalty card price with separate old price`() {
        val blocks = listOf(
            RawTextBlock(
                text = "149.99 ПО КАРТЕ",
                boundingBox = NormalizedRect(0.2f, 0.4f, 0.5f, 0.46f)
            ),
            RawTextBlock(
                text = "199.99 БЕЗ КАРТЫ",
                boundingBox = NormalizedRect(0.2f, 0.47f, 0.5f, 0.52f)
            )
        )

        val result = parser.parse(blocks)
        assertEquals(1, result.size)
        val tag = result[0]
        assertEquals(149.99, tag.finalPrice, 0.001)
        assertNotNull(tag.originalPrice)
        assertEquals(199.99, tag.originalPrice!!, 0.001)
        assertTrue(tag.isLoyaltyPrice)
        assertTrue(tag.toSpeechUtterance().contains("по карте"))
        assertTrue(tag.toSpeechUtterance().contains("обычная"))
    }

    @Test
    fun `test unit of measure per kg`() {
        val blocks = listOf(
            RawTextBlock(
                text = "Яблоки сезонные 129 ₽ / кг",
                boundingBox = NormalizedRect(0.1f, 0.2f, 0.5f, 0.25f)
            )
        )

        val result = parser.parse(blocks)
        assertEquals(1, result.size)
        val tag = result[0]
        assertEquals(129.0, tag.finalPrice, 0.001)
        assertEquals("кг", tag.unitOfMeasure)
        assertEquals("129 рублей за кг", tag.toSpeechUtterance())
    }

    @Test
    fun `test unit of measure per 100g`() {
        val blocks = listOf(
            RawTextBlock(
                text = "Сыр Российский 89 ₽ за 100 г",
                boundingBox = NormalizedRect(0.1f, 0.2f, 0.5f, 0.25f)
            )
        )

        val result = parser.parse(blocks)
        assertEquals(1, result.size)
        val tag = result[0]
        assertEquals(89.0, tag.finalPrice, 0.001)
        assertEquals("100г", tag.unitOfMeasure)
        assertEquals("89 рублей за 100г", tag.toSpeechUtterance())
    }

    @Test
    fun `test ignore barcode and date and sku noise`() {
        val blocks = listOf(
            RawTextBlock(
                text = "EAN 4607001234567 Арт. 98412 Дата 15.10.2026\nМасло сливочное 82.5%\n189 ₽",
                boundingBox = NormalizedRect(0.1f, 0.1f, 0.6f, 0.25f)
            )
        )

        val result = parser.parse(blocks)
        assertEquals(1, result.size)
        val tag = result[0]
        assertEquals(189.0, tag.finalPrice, 0.001)
    }

    @Test
    fun `test spatial clustering separates distant price tags`() {
        val tag1 = RawTextBlock("Хлеб 45 ₽", NormalizedRect(0.1f, 0.1f, 0.3f, 0.15f))
        val tag2 = RawTextBlock("Молоко 85 ₽", NormalizedRect(0.7f, 0.8f, 0.9f, 0.85f))

        val result = parser.parse(listOf(tag1, tag2))
        assertEquals(2, result.size)
        val prices = result.map { it.finalPrice }.toSet()
        assertTrue(prices.contains(45.0))
        assertTrue(prices.contains(85.0))
    }
}
