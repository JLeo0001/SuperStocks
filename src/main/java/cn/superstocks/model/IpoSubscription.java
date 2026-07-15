package cn.superstocks.model;

import java.util.UUID;

public record IpoSubscription(int id, int ipoId, UUID playerId, double shares, long subscribedAt) {}
