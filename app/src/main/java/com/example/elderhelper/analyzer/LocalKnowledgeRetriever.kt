package com.example.elderhelper.analyzer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A small offline lexical RAG store packaged with the app. No network or embedding model is used. */
internal fun interface LocalKnowledgeRetriever {
    fun retrieve(userQuestion: String, screenText: String): LocalKnowledgeAnswer?
}

internal data class LocalKnowledgeAnswer(
    val id: String,
    val guidance: String,
    val capabilityPack: String = "",
    val riskLevel: LocalKnowledgeRiskLevel = LocalKnowledgeRiskLevel.LOW,
    val recoveryInstruction: String? = null,
)

internal enum class LocalKnowledgeRiskLevel {
    LOW,
    GUIDANCE_ONLY,
    URGENT,
}

internal data class LocalKnowledgeCatalogSummary(
    val packCount: Int,
    val entryCount: Int,
    val packNames: Set<String>,
    val taskIds: Set<String>,
)

internal object EmptyLocalKnowledgeRetriever : LocalKnowledgeRetriever {
    override fun retrieve(userQuestion: String, screenText: String): LocalKnowledgeAnswer? = null
}

internal class AssetLocalKnowledgeRetriever(context: Context) : LocalKnowledgeRetriever {
    private val catalog: LocalKnowledgeCatalog by lazy {
        val packs = context.assets.open(PACK_ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
            parsePacks(JSONArray(reader.readText()))
        }
        val entries = context.assets.open(ENTRY_ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
            parseEntries(JSONArray(reader.readText()), packs.taskMetadata)
        }
        val entryIds = entries.mapTo(linkedSetOf(), LocalKnowledgeEntry::id)
        require(entryIds == packs.taskMetadata.keys) {
            val missingEntries = packs.taskMetadata.keys - entryIds
            val missingPackAssignments = entryIds - packs.taskMetadata.keys
            "Offline knowledge pack mismatch. Missing entries=$missingEntries, unassigned entries=$missingPackAssignments"
        }
        LocalKnowledgeCatalog(packs.names, entries)
    }

    internal fun catalogSummary(): LocalKnowledgeCatalogSummary = LocalKnowledgeCatalogSummary(
        packCount = catalog.packNames.size,
        entryCount = catalog.entries.size,
        packNames = catalog.packNames,
        taskIds = catalog.entries.mapTo(linkedSetOf(), LocalKnowledgeEntry::id),
    )

    override fun retrieve(userQuestion: String, screenText: String): LocalKnowledgeAnswer? {
        val question = userQuestion.normalizeForRetrieval()
        if (question.isBlank()) return null
        val visibleText = screenText.normalizeForRetrieval()

        val best = catalog.entries
            .map { entry -> entry to entry.score(question, visibleText) }
            .filter { (_, score) -> score.questionMatches > 0 }
            .maxWithOrNull(compareBy<Pair<LocalKnowledgeEntry, RetrievalScore>> { it.second.total }
                .thenBy { it.first.id })
            ?: return null

        return best.first.let { entry ->
            val visibleLabel = entry.screenLabels.firstOrNull { label ->
                visibleText.contains(label.normalizeForRetrieval())
            }
            val guidance = entry.answerWhenVisible
                ?.takeIf { visibleLabel != null }
                ?.replace("{label}", visibleLabel.orEmpty())
                ?: entry.answer
            LocalKnowledgeAnswer(
                id = entry.id,
                guidance = guidance,
                capabilityPack = entry.capabilityPack,
                riskLevel = entry.riskLevel,
                recoveryInstruction = entry.recoveryInstruction,
            )
        }
    }

    private fun parsePacks(json: JSONArray): ParsedPacks {
        val packIds = mutableSetOf<String>()
        val packNames = linkedSetOf<String>()
        val taskMetadata = linkedMapOf<String, LocalKnowledgeTaskMetadata>()
        for (index in 0 until json.length()) {
            val item = json.getJSONObject(index)
            val packId = item.getString("id")
            val packName = item.getString("name")
            val defaultRiskLevel = item.getString("defaultRiskLevel").toRiskLevel()
            val recoveryInstruction = item.getString("recoveryInstruction")
            val riskOverrides = item.optJSONObject("riskOverrides") ?: JSONObject()
            require(packIds.add(packId)) { "Duplicate offline capability pack id: $packId" }
            require(packNames.add(packName)) { "Duplicate offline capability pack name: $packName" }
            require(recoveryInstruction.isNotBlank()) { "Capability pack $packId has no recovery instruction" }

            readStrings(item.getJSONArray("taskIds")).forEach { taskId ->
                val riskLevel = riskOverrides.optString(taskId)
                    .takeIf(String::isNotBlank)
                    ?.toRiskLevel()
                    ?: defaultRiskLevel
                require(taskMetadata.put(taskId, LocalKnowledgeTaskMetadata(packName, riskLevel, recoveryInstruction)) == null) {
                    "Offline task $taskId belongs to more than one capability pack"
                }
            }
        }
        return ParsedPacks(packNames, taskMetadata)
    }

    private fun parseEntries(
        json: JSONArray,
        taskMetadata: Map<String, LocalKnowledgeTaskMetadata>,
    ): List<LocalKnowledgeEntry> {
        val entryIds = mutableSetOf<String>()
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                val id = item.getString("id")
                require(entryIds.add(id)) { "Duplicate offline knowledge task id: $id" }
                val metadata = requireNotNull(taskMetadata[id]) {
                    "Offline knowledge task $id is not assigned to a capability pack"
                }
                val keywords = item.getJSONArray("keywords")
                val screenLabels = item.optJSONArray("screenLabels")?.let(::readStrings).orEmpty()
                require(keywords.length() >= MIN_SYNONYM_COUNT) {
                    "Offline knowledge task $id needs at least $MIN_SYNONYM_COUNT question synonyms"
                }
                require(screenLabels.size >= MIN_SCREEN_LABEL_COUNT) {
                    "Offline knowledge task $id needs at least $MIN_SCREEN_LABEL_COUNT screen-label variants"
                }
                add(
                    LocalKnowledgeEntry(
                        id = id,
                        keywords = List(keywords.length()) { keywords.getString(it).normalizeForRetrieval() },
                        screenLabels = screenLabels,
                        answerWhenVisible = item.optString("answerWhenVisible").takeIf(String::isNotBlank),
                        answer = item.getString("answer"),
                        capabilityPack = metadata.capabilityPack,
                        riskLevel = metadata.riskLevel,
                        recoveryInstruction = metadata.recoveryInstruction,
                    ),
                )
            }
        }
    }

    private fun readStrings(json: JSONArray): List<String> = List(json.length()) { json.getString(it) }

    private data class LocalKnowledgeEntry(
        val id: String,
        val keywords: List<String>,
        val screenLabels: List<String>,
        val answerWhenVisible: String?,
        val answer: String,
        val capabilityPack: String,
        val riskLevel: LocalKnowledgeRiskLevel,
        val recoveryInstruction: String,
    ) {
        fun score(question: String, visibleText: String): RetrievalScore {
            val questionMatches = keywords.count(question::contains)
            val screenMatches = keywords.count(visibleText::contains)
            val specificity = keywords.filter(question::contains).sumOf(String::length)
            return RetrievalScore(questionMatches, questionMatches * 10 + screenMatches * 3 + specificity)
        }
    }

    private data class RetrievalScore(val questionMatches: Int, val total: Int)

    private data class LocalKnowledgeCatalog(
        val packNames: Set<String>,
        val entries: List<LocalKnowledgeEntry>,
    )

    private data class ParsedPacks(
        val names: Set<String>,
        val taskMetadata: Map<String, LocalKnowledgeTaskMetadata>,
    )

    private data class LocalKnowledgeTaskMetadata(
        val capabilityPack: String,
        val riskLevel: LocalKnowledgeRiskLevel,
        val recoveryInstruction: String,
    )

    private companion object {
        const val ENTRY_ASSET_NAME = "local_help_knowledge.json"
        const val PACK_ASSET_NAME = "local_help_capability_packs.json"
        const val MIN_SYNONYM_COUNT = 3
        const val MIN_SCREEN_LABEL_COUNT = 2
    }
}

private fun String.normalizeForRetrieval(): String = lowercase().replace(Regex("\\s+"), "")

private fun String.toRiskLevel(): LocalKnowledgeRiskLevel = try {
    LocalKnowledgeRiskLevel.valueOf(this)
} catch (error: IllegalArgumentException) {
    throw IllegalArgumentException("Unknown offline knowledge risk level: $this", error)
}
