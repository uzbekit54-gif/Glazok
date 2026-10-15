package com.glazok.core.domain.parser

import com.glazok.core.domain.model.NormalizedRect
import com.glazok.core.domain.model.PriceTag
import com.glazok.core.domain.model.RawTextBlock

/**
 * Интеллектуальный парсер ценников.
 * Реализует двухшаговый алгоритм:
 * 1. Пространственная кластеризация текстовых блоков OCR (Spatial Clustering).
 * 2. Взвешенный семантический анализ (Weighted Regex & Heuristics) с акцентом на акции и цены по картам.
 */
class SmartPriceParser {

    private val dateRegex = Regex("""\b\d{1,2}[./]\d{1,2}[./]\d{2,4}\b""")
    private val barcodeSkuRegex = Regex("""(?i)\b(?:ean|арт\.?|sku|код)\s*[:№]?\s*\d+\b|\b\d{8,14}\b""")
    private val unitRegex = Regex("""(?i)(?:за|/|\b)\s*(100\s*г|100г|кг|килограмм|шт|штуку|уп|л|литр|мл)\b""")

    // Паттерны поиска цен: число с символом валюты или изолированное число в строке с валютой
    private val priceWithCurrencyRegex = Regex("""(\d{1,5}(?:[.,]\d{1,2})?)\s*(?:₽|руб\.?|р\b)""", RegexOption.IGNORE_CASE)
    private val isolatedNumberRegex = Regex("""\b(\d{1,5}(?:[.,]\d{1,2})?)\b""")

    private val loyaltyKeywords = listOf("по карте", "карта", "с картой", "клуб", "выручай", "лента", "окей")
    private val regularKeywords = listOf("без карты", "старая цена", "обычная", "до акции")

    /**
     * Основная точка входа. Принимает список сырых блоков OCR и возвращает распознанные ценники.
     */
    fun parse(rawBlocks: List<RawTextBlock>): List<PriceTag> {
        if (rawBlocks.isEmpty()) return emptyList()

        val clusters = clusterBlocks(rawBlocks)
        val priceTags = mutableListOf<PriceTag>()

        for (cluster in clusters) {
            val tag = parseCluster(cluster)
            if (tag != null) {
                priceTags.add(tag)
            }
        }

        return priceTags
    }

    private fun clusterBlocks(blocks: List<RawTextBlock>): List<List<RawTextBlock>> {
        val clusters = mutableListOf<MutableList<RawTextBlock>>()
        val visited = BooleanArray(blocks.size)

        for (i in blocks.indices) {
            if (visited[i]) continue
            val currentCluster = mutableListOf<RawTextBlock>()
            val queue = ArrayDeque<Int>()
            queue.add(i)
            visited[i] = true

            while (queue.isNotEmpty()) {
                val currIdx = queue.removeFirst()
                val currBlock = blocks[currIdx]
                currentCluster.add(currBlock)

                for (j in blocks.indices) {
                    if (!visited[j]) {
                        val other = blocks[j]
                        if (currBlock.boundingBox.isVerticallyClose(other.boundingBox, maxVerticalGap = 0.12f) &&
                            (currBlock.boundingBox.overlapsHorizontally(other.boundingBox) ||
                             currBlock.boundingBox.distanceTo(other.boundingBox) < 0.25f)
                        ) {
                            visited[j] = true
                            queue.add(j)
                        }
                    }
                }
            }
            clusters.add(currentCluster)
        }
        return clusters
    }

    private fun parseCluster(cluster: List<RawTextBlock>): PriceTag? {
        val combinedText = cluster.joinToString("\n") { it.text }
        val clusterBox = computeBoundingBox(cluster)

        // 1. Очистка текста от дат и штрихкодов
        var cleanText = dateRegex.replace(combinedText, " ")
        cleanText = barcodeSkuRegex.replace(cleanText, " ")

        // 2. Поиск единицы измерения
        val unitMatch = unitRegex.find(cleanText)
        val unitOfMeasure = unitMatch?.groupValues?.get(1)?.lowercase()?.replace(" ", "")

        // 3. Анализ строк на наличие цен и контекста акций
        val lines = cleanText.lines()
        val detectedPrices = mutableListOf<CandidatePrice>()

        for (line in lines) {
            val lowerLine = line.lowercase().trim()
            if (lowerLine.isBlank()) continue

            val isLoyaltyLine = loyaltyKeywords.any { lowerLine.contains(it) }
            val isRegularLine = regularKeywords.any { lowerLine.contains(it) }

            val match = priceWithCurrencyRegex.find(line)
            if (match != null) {
                val value = parseNumber(match.groupValues[1])
                if (value != null && value > 0.0) {
                    detectedPrices.add(CandidatePrice(value, isLoyaltyLine, isRegularLine))
                }
            } else if (isLoyaltyLine || isRegularLine || lowerLine.contains("скидка") || lowerLine.contains("акция")) {
                // Если валюта не указана прямо в строке, но есть контекст скидки/карты
                val numMatches = isolatedNumberRegex.findAll(line)
                for (numMatch in numMatches) {
                    val value = parseNumber(numMatch.groupValues[1])
                    if (value != null && value in 1.0..99999.0) {
                        detectedPrices.add(CandidatePrice(value, isLoyaltyLine, isRegularLine))
                    }
                }
            }
        }

        if (detectedPrices.isEmpty()) return null

        // 4. Определение финальной цены и цены до скидки/без карты
        val finalPrice: Double
        val originalPrice: Double?
        val isLoyalty: Boolean

        val loyaltyCandidates = detectedPrices.filter { it.isLoyalty }
        val regularCandidates = detectedPrices.filter { it.isRegular }

        if (loyaltyCandidates.isNotEmpty() && regularCandidates.isNotEmpty()) {
            finalPrice = loyaltyCandidates.minOf { it.value }
            originalPrice = regularCandidates.maxOf { it.value }
            isLoyalty = true
        } else if (detectedPrices.size >= 2) {
            val minP = detectedPrices.minOf { it.value }
            val maxP = detectedPrices.maxOf { it.value }
            if (minP < maxP) {
                finalPrice = minP
                originalPrice = maxP
                isLoyalty = loyaltyCandidates.isNotEmpty()
            } else {
                finalPrice = minP
                originalPrice = null
                isLoyalty = false
            }
        } else {
            val single = detectedPrices.first()
            finalPrice = single.value
            originalPrice = null
            isLoyalty = single.isLoyalty
        }

        return PriceTag(
            finalPrice = finalPrice,
            originalPrice = originalPrice,
            isLoyaltyPrice = isLoyalty,
            unitOfMeasure = unitOfMeasure,
            currency = "RUB",
            boundingBox = clusterBox,
            rawText = combinedText
        )
    }

    private fun parseNumber(str: String): Double? {
        val sanitized = str.replace(',', '.').trim()
        return sanitized.toDoubleOrNull()
    }

    private fun computeBoundingBox(cluster: List<RawTextBlock>): NormalizedRect {
        val minLeft = cluster.minOf { it.boundingBox.left }
        val minTop = cluster.minOf { it.boundingBox.top }
        val maxRight = cluster.maxOf { it.boundingBox.right }
        val maxBottom = cluster.maxOf { it.boundingBox.bottom }
        return NormalizedRect(minLeft, minTop, maxRight, maxBottom)
    }

    private data class CandidatePrice(
        val value: Double,
        val isLoyalty: Boolean,
        val isRegular: Boolean
    )
}
