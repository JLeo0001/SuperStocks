package cn.superstocks.stock;

import cn.superstocks.model.StockQuote;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public interface StockProvider {
    Map<String, StockQuote> fetch(Collection<String> symbols, Map<String, String> symbolMarkets) throws IOException, InterruptedException;
}
