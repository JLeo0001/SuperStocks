package cn.superstocks.gui;

public record GuiAction(ActionType type, String value, double amount) {
    public static GuiAction market(String market) {
        return new GuiAction(ActionType.MARKET, market, 0.0D);
    }

    public static GuiAction stock(String symbol) {
        return new GuiAction(ActionType.STOCK, symbol, 0.0D);
    }

    public static GuiAction buy(String symbol, double amount) {
        return new GuiAction(ActionType.BUY, symbol, amount);
    }

    public static GuiAction sell(String symbol, double amount) {
        return new GuiAction(ActionType.SELL, symbol, amount);
    }

    public static GuiAction portfolio() {
        return new GuiAction(ActionType.PORTFOLIO, "", 0.0D);
    }

    public static GuiAction ranking(String type) {
        return new GuiAction(ActionType.RANKING, type, 0.0D);
    }

    public enum ActionType {
        MARKET,
        STOCK,
        BUY,
        SELL,
        PORTFOLIO,
        RANKING
    }
}
