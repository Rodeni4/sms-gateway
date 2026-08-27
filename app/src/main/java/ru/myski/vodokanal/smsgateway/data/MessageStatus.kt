package ru.myski.vodokanal.smsgateway.data

import org.json.JSONArray
import org.json.JSONObject

enum class SmsStatus {
    QUEUED, SENT, SEND_FAILED, DELIVERY_PENDING, DELIVERED, DELIVERY_FAILED, DELIVERY_UNKNOWN
}

data class MessageStatus(
    val messageId: String,
    val recipient: String,
    var status: SmsStatus,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val totalParts: Int,
    var sentParts: Int = 0,
    var deliveredParts: Int = 0,
    var deliveredIndices: MutableSet<Int> = mutableSetOf(),
    var rawStatus: Int? = null,
    var androidResultCode: Int? = null,
    var errorMessage: String? = null,
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("messageId", messageId)
            put("recipient", recipient)
            put("status", status.name)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("totalParts", totalParts)
            put("sentParts", sentParts)
            put("deliveredParts", deliveredParts)
            put("deliveredIndices", JSONArray(deliveredIndices))
            put("rawStatus", rawStatus ?: JSONObject.NULL)
            put("androidResultCode", androidResultCode ?: JSONObject.NULL)
            put("errorMessage", errorMessage ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): MessageStatus {
            val indices = mutableSetOf<Int>()
            val indicesArray = json.optJSONArray("deliveredIndices")
            if (indicesArray != null) {
                for (i in 0 until indicesArray.length()) {
                    indices.add(indicesArray.getInt(i))
                }
            }

            return MessageStatus(
                messageId = json.getString("messageId"),
                recipient = json.getString("recipient"),
                status = SmsStatus.valueOf(json.getString("status")),
                createdAt = json.getLong("createdAt"),
                updatedAt = json.getLong("updatedAt"),
                totalParts = json.getInt("totalParts"),
                sentParts = json.getInt("sentParts"),
                deliveredParts = json.getInt("deliveredParts"),
                deliveredIndices = indices,
                rawStatus = if (json.isNull("rawStatus")) null else json.getInt("rawStatus"),
                androidResultCode = if (json.isNull("androidResultCode")) null else json.getInt("androidResultCode"),
                errorMessage = if (json.isNull("errorMessage")) null else json.getString("errorMessage"),
            )
        }
    }
}
