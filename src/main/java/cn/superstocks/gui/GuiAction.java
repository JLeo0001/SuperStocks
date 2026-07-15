package cn.superstocks.gui;

public record GuiAction(ActionType type, String value, double amount) {
    public static GuiAction market(String market) {
        return new GuiAction(ActionType.MARKET, market, 0.0D);
    }

    public static GuiAction mainPage(int page) {
        return new GuiAction(ActionType.MAIN_PAGE, String.valueOf(page), 0.0D);
    }

    public static GuiAction marketPage(String market, int page) {
        return new GuiAction(ActionType.MARKET_PAGE, market + "|" + page, 0.0D);
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

    public static GuiAction confirmBuy(String symbol, double amount) {
        return new GuiAction(ActionType.CONFIRM_BUY, symbol, amount);
    }

    public static GuiAction confirmSell(String symbol, double amount) {
        return new GuiAction(ActionType.CONFIRM_SELL, symbol, amount);
    }

    public static GuiAction portfolio() {
        return new GuiAction(ActionType.PORTFOLIO, "", 0.0D);
    }

    public static GuiAction watchlist() {
        return watchlist(0);
    }

    public static GuiAction watchlist(int page) {
        return new GuiAction(ActionType.WATCHLIST, String.valueOf(page), 0.0D);
    }

    public static GuiAction ranking(String type) {
        return new GuiAction(ActionType.RANKING, type, 0.0D);
    }

    public enum ActionType {
        MARKET,
        MAIN_PAGE,
        MARKET_PAGE,
        STOCK,
        BUY,
        SELL,
        CONFIRM_BUY,
        CONFIRM_SELL,
        PORTFOLIO,
        WATCHLIST,
        RANKING
    }
}
