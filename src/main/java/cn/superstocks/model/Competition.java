package cn.superstocks.model;

public record Competition(int id, String name, double startCapital, long startedAt, long endsAt, String status) {
    public boolean active() { return "ACTIVE".equals(status); }
    public boolean ended() { return "ENDED".equals(status); }
}
