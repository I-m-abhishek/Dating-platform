package com.dating.platform.match.entity;

public enum MatchSource {
    /** Both people liked each other. */
    MUTUAL_LIKE,
    /** The weekly run that every user gets for free. */
    AUTO_MATCH_WEEKLY,
    /** The daily run, a Premium entitlement. */
    AUTO_MATCH_DAILY,
    /** Created by a moderator or support tool. */
    MANUAL
}
