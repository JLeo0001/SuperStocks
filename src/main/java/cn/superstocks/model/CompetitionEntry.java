package cn.superstocks.model;

import java.util.UUID;

public record CompetitionEntry(int id, int competitionId, UUID playerId, double cash, long joinedAt) {}
