package manhunt.game;

public enum ManhuntState {
    PREGAME("§a", "PRE-GAME"),
    PREPARING("§2", "PREPARING"),
    PLAYING("§6", "IN-GAME"),
    POSTGAME("§e", "POST-GAME");

    private final String color;
    private final String motd;

    ManhuntState(String color, String motd) {
        this.color = color;
        this.motd = motd;
    }

    public String getColor() {
        return color;
    }

    public String getMotd() {
        return motd;
    }
}
