package com.asksin.analyzer.model

enum class SequenceType(val label: String) {
    PAIRING("Pairing"),
    CONFIG_READ("Config Read"),
    CONFIG_WRITE("Config Write"),
    AES_HANDSHAKE("AES Handshake"),
    KEY_EXCHANGE("Key Exchange"),
    COMMAND_STATUS("Command+Status"),
    GET_INFO("GET→INFO"),
    EVENT_ACK("Event+ACK"),
    GENERIC_BIDI("Request/Response")
}

data class TelegramSequence(
    val id: Long,                    // first telegram's id
    val type: SequenceType,
    val telegramIds: List<Long>,     // ordered member IDs
    val isComplete: Boolean
)
