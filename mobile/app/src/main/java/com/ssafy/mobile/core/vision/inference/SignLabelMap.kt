package com.ssafy.mobile.core.vision.inference

class SignLabelMap private constructor(
    private val labelsByIndex: Map<Int, String>,
) {
    val size: Int
        get() = labelsByIndex.size

    fun glossFor(classIndex: Int): String =
        labelsByIndex[classIndex] ?: SignModelContract.UNKNOWN_GLOSS

    companion object {
        fun parse(json: String): SignLabelMap {
            val trimmedJson = json.trim()
            val labels =
                when {
                    trimmedJson.startsWith('[') -> parseArrayLabels(trimmedJson)
                    trimmedJson.contains(LABELS_PROPERTY) ->
                        parseArrayLabels(
                            extractLabelsArray(trimmedJson),
                        )
                    else -> parseIndexedLabels(trimmedJson)
                }

            require(labels.isNotEmpty()) {
                "Label map must contain at least one gloss."
            }

            return SignLabelMap(labels)
        }

        private fun parseArrayLabels(jsonArray: String): Map<Int, String> =
            STRING_VALUE_REGEX
                .findAll(jsonArray)
                .mapIndexed { index, matchResult ->
                    index to matchResult.groupValues[STRING_VALUE_GROUP].unescapeJsonString()
                }.toMap()

        private fun parseIndexedLabels(jsonObject: String): Map<Int, String> =
            INDEXED_LABEL_REGEX
                .findAll(jsonObject)
                .associate { matchResult ->
                    val index = matchResult.groupValues[INDEX_GROUP].toInt()
                    val gloss = matchResult.groupValues[INDEXED_LABEL_GROUP].unescapeJsonString()
                    index to gloss
                }

        private fun extractLabelsArray(jsonObject: String): String {
            val labelsPropertyIndex = jsonObject.indexOf(LABELS_PROPERTY)
            require(labelsPropertyIndex >= 0) {
                "Could not find labels property."
            }
            val startIndex = jsonObject.indexOf('[', startIndex = labelsPropertyIndex)
            require(startIndex >= 0) {
                "Could not find labels array."
            }

            var depth = 0
            for (index in startIndex until jsonObject.length) {
                when (jsonObject[index]) {
                    '[' -> depth += 1
                    ']' -> {
                        depth -= 1
                        if (depth == 0) {
                            return jsonObject.substring(startIndex, index + 1)
                        }
                    }
                }
            }
            error("Could not find labels array end.")
        }

        private fun String.unescapeJsonString(): String =
            replace("\\\"", "\"")
                .replace("\\\\", "\\")

        private const val LABELS_PROPERTY = "\"labels\""
        private const val STRING_VALUE_GROUP = 1
        private const val INDEX_GROUP = 1
        private const val INDEXED_LABEL_GROUP = 2
        private val STRING_VALUE_REGEX = Regex("\"((?:\\\\.|[^\"])*)\"")
        private val INDEXED_LABEL_REGEX = Regex("\"(\\d+)\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
    }
}
