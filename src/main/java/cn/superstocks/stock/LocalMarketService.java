package cn.superstocks.stock;

import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.StockQuote;
import cn.superstocks.storage.StockStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class LocalMarketService {
    private final JavaPlugin plugin; private final StockStorage storage; private final LanguageManager language;
    private final Map<String,LocalMarket> markets=new LinkedHashMap<>(); private final Map<String,LocalStock> stocks=new LinkedHashMap<>();
    public LocalMarketService(JavaPlugin plugin,StockStorage storage,LanguageManager language){this.plugin=plugin;this.storage=storage;this.language=language;}
    public void ensureExampleFile(){File f=folder();if(!f.exists()&&!f.mkdirs())return;File e=new File(f,"example.yml");if(!e.exists()&&plugin.getResource("CustomMarkets/example.yml")!=null)plugin.saveResource("CustomMarkets/example.yml",false);}
    public void reload(){markets.clear();stocks.clear();File[] files=folder().listFiles((d,n)->n.endsWith(".yml")||n.endsWith(".yaml"));if(files==null)return;for(File f:files)loadFile(f);}
    public Map<String,String> marketNames(){Map<String,String> r=new LinkedHashMap<>();markets.values().stream().filter(m->m.enabled).forEach(m->r.put(m.id,m.name));return r;}
    public Map<String,String> marketIcons(){Map<String,String> r=new LinkedHashMap<>();markets.values().stream().filter(m->m.enabled).forEach(m->r.put(m.id,m.icon));return r;}
    public Map<String,List<String>> marketSymbols(){Map<String,List<String>> r=new LinkedHashMap<>();stocks.values().stream().filter(LocalStock::enabled).forEach(s->r.computeIfAbsent(s.market.id,x->new ArrayList<>()).add(s.symbol));return r;}
    public Map<String,String> symbolMarkets(){Map<String,String> r=new HashMap<>();stocks.values().stream().filter(LocalStock::enabled).forEach(s->r.put(s.symbol,s.market.id));return r;}
    public Map<String,StockQuote> tick(){Map<String,StockQuote> r=new HashMap<>();long now=System.currentTimeMillis();for(LocalStock s:stocks.values())if(s.enabled()){if(now-s.lastTick>=s.market.tickSeconds*1000L)s.tick(now);r.put(s.symbol,s.quote());}return r;}

    private void broadcastEvent(String symbol,String name,double impact){if(language==null)return;String key=impact>=0?"messages.market-event-positive":"messages.market-event-negative";Map<String,String> vars=language.vars("name",name,"symbol",symbol,"impact",formatPct(Math.abs(impact)));String msg=language.text(key,vars);Bukkit.getScheduler().runTask(plugin,()->Bukkit.broadcastMessage(msg));}
    private static String formatPct(double v){return String.format("%.2f",v);}

    private void loadFile(File file){YamlConfiguration y=YamlConfiguration.loadConfiguration(file);String id=y.getString("market.id",file.getName().replaceFirst("\\.ya?ml$",""));double limit=y.contains("simulation.tick-limit-percent")?y.getDouble("simulation.tick-limit-percent",15):y.getDouble("simulation.daily-limit-percent",15);LocalMarket m=new LocalMarket(id,y.getString("market.display-name",id),y.getString("market.icon","AMETHYST_SHARD"),y.getBoolean("market.enabled",true),Math.max(10,y.getLong("simulation.tick-seconds",300)),limit,y.getBoolean("simulation.regimes.enabled",true),y.getLong("simulation.regimes.duration-seconds",3600));markets.put(id,m);ConfigurationSection sec=y.getConfigurationSection("stocks");if(sec==null)return;for(String key:sec.getKeys(false)){String p=key+".";String symbol=sec.getString(p+"symbol",key);LocalStock s=new LocalStock(m,symbol,sec.getString(p+"name",symbol),sec.getBoolean(p+"enabled",true),Math.max(.01,sec.getDouble(p+"initial-price",100)),Math.max(.01,sec.getDouble(p+"min-price",1)),sec.getDouble(p+"max-price",10000),sec.getDouble(p+"drift-percent-per-tick",0),Math.max(0,sec.getDouble(p+"volatility-percent",2)));try{storage.quoteState(symbol).ifPresent(s::restore);}catch(Exception e){plugin.getLogger().warning("恢复本地行情失败: "+symbol+" - "+e.getMessage());}stocks.put(symbol,s);}}
    private File folder(){return new File(plugin.getDataFolder(),"CustomMarkets");}

    private final class LocalStock {final LocalMarket market;final String symbol,name;final boolean active;final double min,max,drift,volatility;double price,change,pct;long lastTick;Instant updated=Instant.now();String regime="neutral";long regimeUntil;
        LocalStock(LocalMarket m,String s,String n,boolean a,double price,double min,double max,double d,double v){market=m;symbol=s;name=n;active=a;this.price=price;this.min=min;this.max=Math.max(min,max);drift=d;volatility=v;}
        boolean enabled(){return active&&market.enabled;}
        void restore(StockStorage.StockQuoteState q){price=q.price();change=q.change();pct=q.changePercent();lastTick=q.updatedAt();updated=Instant.ofEpochMilli(q.updatedAt());if(q.regime()!=null&&!q.regime().isBlank())regime=q.regime();regimeUntil=q.regimeUntil();}
        void tick(long now){if(market.regimes&&now>=regimeUntil){double x=ThreadLocalRandom.current().nextDouble();regime=x<.25?"bull":x<.5?"bear":"neutral";regimeUntil=now+market.regimeSeconds*1000L;}double dm="bull".equals(regime)?1.5:"bear".equals(regime)?-1.2:1;double vm="bull".equals(regime)?.8:"bear".equals(regime)?1.5:1;double event=eventImpact();if(Math.abs(event)>=plugin.getConfig().getDouble("market-events.announce-threshold-percent",0)+1e-6){broadcastEvent(symbol,name,event);}double move=drift*dm+ThreadLocalRandom.current().nextGaussian()*volatility*vm+event;move=Math.max(-market.limit,Math.min(market.limit,move));double old=price;price=Math.max(min,Math.min(max,price*(1+move/100)));change=price-old;pct=old<=0?0:change/old*100;lastTick=now;updated=Instant.now();try{storage.saveQuoteState(new StockStorage.StockQuoteState(symbol,name,market.id,price,old,change,pct,now,regime,regimeUntil,0));}catch(Exception e){plugin.getLogger().warning("保存本地行情失败: "+symbol+" - "+e.getMessage());}}
        double eventImpact(){ConfigurationSection e=plugin.getConfig().getConfigurationSection("market-events");if(e==null||!e.getBoolean("enabled",false)||ThreadLocalRandom.current().nextDouble(100)>=e.getDouble("chance-percent-per-tick",2))return 0;double min=e.getDouble("min-impact-percent",-5),max=e.getDouble("max-impact-percent",5);return min==max?min:ThreadLocalRandom.current().nextDouble(Math.min(min,max),Math.max(min,max));}
        StockQuote quote(){return new StockQuote(symbol,name,market.id,price,change,pct,updated);}}
    private record LocalMarket(String id,String name,String icon,boolean enabled,long tickSeconds,double limit,boolean regimes,long regimeSeconds){}
}
