package com.asksin.analyzer.model

sealed class TelegramListItem {
    data class Single(val telegram: Telegram) : TelegramListItem()
    data class GroupHeader(
        val sequence: TelegramSequence,
        val telegrams: List<Telegram>,
        val expanded: Boolean
    ) : TelegramListItem()
    data class GroupMember(
        val telegram: Telegram,
        val sequence: TelegramSequence,
        val isLast: Boolean
    ) : TelegramListItem()
}
