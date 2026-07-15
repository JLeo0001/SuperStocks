package cn.superstocks.stock;

import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.PricePoint;
import cn.superstocks.model.StockQuote;
import cn.superstocks.storage.StockStorage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class StockService {
    private final JavaPlugin plugin;
    private final StockStorage storage;
    private final LanguageManager language;
    private StockProvider provider;
    private VolatilityEngine volatilityEngine;
    private LocalMarketService localMarketService;
    private String providerName;
    private final Map<String,String> marketNames=new LinkedHashMap<>();
    private final Map<String,List<String>> marketSymbols=new LinkedHashMap<>();
    private final Map<String,String> marketIcons=new LinkedHashMap<>();
    private final Map<String,String> symbolMarkets=new ConcurrentHashMap<>();
    private final Map<String,StockQuote> rawCache=new ConcurrentHashMap<>();
    private final Map<String,StockQuote> cache=new ConcurrentHashMap<>();
    private final Map<String,Long> frozenUntil=new ConcurrentHashMap<>();
    private BukkitTask externalTask,volatilityTask,localMarketTask;
    private volatile Instant lastSync;
    private volatile int lastFetchedCount;
    private volatile boolean manuallyPaused;

    public StockService(JavaPlugin plugin, StockStorage storage, LanguageManager language){this.plugin=plugin;this.storage=storage;this.language=language;reload();}

    public synchronized void reload(){
        stopTasks(); reloadProvider();
        if(localMarketService==null)localMarketService=new LocalMarketService(plugin,storage,language);
        localMarketService.ensureExampleFile(); localMarketService.reload(); reloadMarkets();
        if(volatilityEngine==null)volatilityEngine=new VolatilityEngine(plugin);else volatilityEngine.reload();
        restorePersistedQuotes(); recalculateAdjustedCache();
    }

    private void reloadProvider(){ConfigurationSection s=plugin.getConfig().getConfigurationSection("stock-provider");if(s==null)throw new IllegalStateException("Missing stock-provider");String active=s.getString("active","tencent").toLowerCase();ConfigurationSection source=s.getConfigurationSection("sources."+active);if(source==null){active="tencent";source=s.getConfigurationSection("sources.tencent");}if(source==null)source=s;providerName=source.getString("display-name",active);provider=new TencentStockProvider(source,s.getInt("timeout-seconds",8));}

    public synchronized void reloadMarkets(){marketNames.clear();marketSymbols.clear();marketIcons.clear();symbolMarkets.clear();ConfigurationSection root=plugin.getConfig().getConfigurationSection("markets");if(root!=null)for(String m:root.getKeys(false)){List<String> symbols=new ArrayList<>(root.getStringList(m+".symbols"));marketNames.put(m,root.getString(m+".display-name",m));marketIcons.put(m,root.getString(m+".icon","EMERALD"));marketSymbols.put(m,List.copyOf(symbols));for(String symbol:symbols)symbolMarkets.put(symbol,m);}if(plugin.getConfig().getBoolean("custom-markets.enabled",true)){marketNames.putAll(localMarketService.marketNames());marketIcons.putAll(localMarketService.marketIcons());localMarketService.marketSymbols().forEach((k,v)->marketSymbols.put(k,List.copyOf(v)));symbolMarkets.putAll(localMarketService.symbolMarkets());}}

    public synchronized void start(){stopTasks();long ext=Math.max(60L,plugin.getConfig().getLong("stock-provider.refresh-seconds",300)*20L);externalTask=plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,this::refreshNow,20L,ext);if(volatilityEngine.randomEnabled()){long t=Math.max(200L,volatilityEngine.randomRefreshSeconds()*20L);volatilityTask=plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,this::recalculateAdjustedCache,t,t);}if(plugin.getConfig().getBoolean("custom-markets.enabled",true)){long t=Math.max(200L,plugin.getConfig().getLong("custom-markets.global-tick-seconds",300)*20L);localMarketTask=plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,()->{tickLocalMarkets();recalculateAdjustedCache();},20L,t);}}
    public synchronized void stopTasks(){if(externalTask!=null)externalTask.cancel();if(volatilityTask!=null)volatilityTask.cancel();if(localMarketTask!=null)localMarketTask.cancel();externalTask=volatilityTask=localMarketTask=null;}

    public void refreshNow(){try{Map<String,String> external=new HashMap<>(symbolMarkets);for(String s:localMarketService.symbolMarkets().keySet())external.remove(s);Map<String,StockQuote> fetched=provider.fetch(external.keySet(),external);int accepted=0;for(StockQuote q:fetched.values())if(acceptRaw(q)){rawCache.put(q.symbol(),q);accepted++;}tickLocalMarkets();recalculateAdjustedCache();lastFetchedCount=accepted;lastSync=Instant.now();if(plugin.getConfig().getBoolean("stock-provider.log-success",true))plugin.getLogger().info("已同步 "+accepted+" 条外部行情，缓存 "+loadedTotal()+"/"+configuredTotal());}catch(Exception e){plugin.getLogger().log(Level.WARNING,"同步股票行情失败，将使用旧缓存",e);}}

    private boolean acceptRaw(StockQuote q){if(!q.valid())return false;StockQuote old=rawCache.get(q.symbol());double max=plugin.getConfig().getDouble("quote-safety.max-change-percent-per-sync",25.0);if(plugin.getConfig().getBoolean("quote-safety.reject-abnormal-change",true)&&old!=null&&old.price()>0){double pct=Math.abs((q.price()-old.price())/old.price()*100);if(pct>max){long freeze=Math.max(0,plugin.getConfig().getLong("quote-safety.circuit-breaker.freeze-seconds",600))*1000L;frozenUntil.put(q.symbol(),System.currentTimeMillis()+freeze);plugin.getLogger().warning("拒绝异常行情 "+q.symbol()+"，单次变化 "+String.format("%.2f",pct)+"%");return false;}}return true;}

    private void tickLocalMarkets(){if(plugin.getConfig().getBoolean("custom-markets.enabled",true))rawCache.putAll(localMarketService.tick());}

    private synchronized Map<String,StockQuote> recalculateAdjustedCache(){Map<String,StockQuote> adjusted=new HashMap<>();long now=System.currentTimeMillis();for(var e:rawCache.entrySet()){StockQuote q=volatilityEngine==null?e.getValue():volatilityEngine.apply(e.getValue());StockQuote previous=cache.get(e.getKey());if(previous!=null&&circuitBreak(previous,q,now)){q=previous;frozenUntil.put(q.symbol(),now+plugin.getConfig().getLong("quote-safety.circuit-breaker.freeze-seconds",600)*1000L);}adjusted.put(e.getKey(),q);persistQuote(q,previous,now);}cache.putAll(adjusted);return adjusted;}

    private boolean circuitBreak(StockQuote old,StockQuote next,long now){if(!plugin.getConfig().getBoolean("quote-safety.circuit-breaker.enabled",true)||old.price()<=0)return false;double max=plugin.getConfig().getDouble("quote-safety.circuit-breaker.stock-max-change-percent",15);return Math.abs((next.price()-old.price())/old.price()*100)>max&&frozenUntil.getOrDefault(next.symbol(),0L)<=now;}
    private void persistQuote(StockQuote q,StockQuote previous,long now){try{boolean local=localMarketService!=null&&localMarketService.symbolMarkets().containsKey(q.symbol());if(!local){double old=previous==null?q.price():previous.price();storage.saveQuoteState(new StockStorage.StockQuoteState(q.symbol(),q.name(),q.market(),q.price(),old,q.change(),q.changePercent(),q.updatedAt().toEpochMilli(),"",0,frozenUntil.getOrDefault(q.symbol(),0L)));}if(plugin.getConfig().getBoolean("price-history.enabled",true))storage.recordPrice(q.symbol(),q.price(),now);}catch(Exception e){plugin.getLogger().log(Level.FINE,"保存行情状态失败",e);}}
    private void restorePersistedQuotes(){for(String symbol:symbolMarkets.keySet())try{storage.quoteState(symbol).ifPresent(s->{StockQuote q=new StockQuote(s.symbol(),s.name(),s.market(),s.price(),s.change(),s.changePercent(),Instant.ofEpochMilli(s.updatedAt()));rawCache.put(symbol,q);cache.put(symbol,q);if(s.frozenUntil()>0)frozenUntil.put(symbol,s.frozenUntil());});}catch(Exception e){plugin.getLogger().log(Level.WARNING,"恢复行情失败: "+symbol,e);}}

    public Optional<StockQuote> quote(String symbol){StockQuote q=cache.get(symbol);if(q==null)q=cache.get(symbol.toLowerCase());return Optional.ofNullable(q);}
    public TradeAvailability tradeAvailability(String symbol,boolean buy){StockQuote q=cache.get(symbol);if(q==null)return new TradeAvailability(false,"NO_QUOTE");if(manuallyPaused||plugin.getConfig().getBoolean("trading.paused",false))return new TradeAvailability(false,"PAUSED");if(frozenUntil.getOrDefault(symbol,0L)>System.currentTimeMillis())return new TradeAvailability(false,"FROZEN");if(plugin.getConfig().getBoolean("quote-safety.reject-stale-quotes",true)){long age=Duration.between(q.updatedAt(),Instant.now()).getSeconds();if(age>plugin.getConfig().getLong("quote-safety.max-quote-age-seconds",900))return new TradeAvailability(false,"STALE");}String market=symbolMarkets.get(symbol);ConfigurationSection h=plugin.getConfig().getConfigurationSection("market-hours."+market);if(h!=null&&h.getBoolean("enabled",false)){try{ZoneId z=ZoneId.of(h.getString("timezone","UTC"));ZonedDateTime n=ZonedDateTime.now(z);if(!h.getBoolean("weekends",false)&&(n.getDayOfWeek()==DayOfWeek.SATURDAY||n.getDayOfWeek()==DayOfWeek.SUNDAY))return new TradeAvailability(false,"CLOSED");LocalTime open=LocalTime.parse(h.getString("open","09:30")),close=LocalTime.parse(h.getString("close","16:00"));if(n.toLocalTime().isBefore(open)||n.toLocalTime().isAfter(close)){String action=h.getString("closed-action","DENY");if(!("SELL_ONLY".equalsIgnoreCase(action)&&!buy))return new TradeAvailability(false,"CLOSED");}}catch(Exception ignored){}}return new TradeAvailability(true,"OK");}
    public record TradeAvailability(boolean allowed,String reason){}

    public void setPaused(boolean paused){manuallyPaused=paused;}
    public boolean paused(){return manuallyPaused||plugin.getConfig().getBoolean("trading.paused",false);}
    public List<PricePoint> history(String symbol,long since,int limit){try{return storage.priceHistory(symbol,since,limit);}catch(Exception e){return List.of();}}
    public Map<String,String> marketNames(){return Collections.unmodifiableMap(marketNames);}public List<String> symbolsForMarket(String m){return marketSymbols.getOrDefault(m,List.of());}public String marketName(String m){return marketNames.getOrDefault(m,m);}public String marketIcon(String m){return marketIcons.getOrDefault(m,"EMERALD");}public int configuredTotal(){return symbolMarkets.size();}public int configuredCount(String m){return symbolsForMarket(m).size();}public int loadedTotal(){return (int)symbolMarkets.keySet().stream().filter(cache::containsKey).count();}public int loadedCount(String m){return (int)symbolsForMarket(m).stream().filter(cache::containsKey).count();}public int lastFetchedCount(){return lastFetchedCount;}public Instant lastSync(){return lastSync;}public String providerName(){return providerName;}public Map<String,Double> priceSnapshot(){Map<String,Double> p=new HashMap<>();cache.forEach((k,v)->p.put(k,v.price()));return p;}public Set<String> symbols(){return Collections.unmodifiableSet(symbolMarkets.keySet());}
}
