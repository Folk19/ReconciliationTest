package trades.testCase.consistency;

import io.vertx.core.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.ValueSource;
import trades.util.HttpClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static trades.util.Config.loadConfig;

public class ConsistencyTest {
    private final static Logger log = LoggerFactory.getLogger(ConsistencyTest.class);

    public static Vertx vertx;
    public static Map<String, JsonObject> apiInfo;

    @BeforeAll
    static void setup() {
        log.info("@BeforeAll - executes once before all test methods in this class");

        vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(10));

        //Read config file and parse API info into map
        JsonObject config = loadConfig("config.json");
        if (Objects.nonNull(config)) {
            apiInfo = new HashMap<>();
            for (Object obj : config.getJsonArray("api")) {
                JsonObject api = (JsonObject) obj;
                if (api.containsKey("name")) {
                    apiInfo.put(api.getString("name"), api);
                }
            }
        }
    }

    @AfterAll
    public static void done() {
        log.info("@AfterAll - executed after all test methods.");
        vertx.close();
    }

    @BeforeEach
    void init() {
        log.info("@BeforeEach - executes before each test method in this class");
        log.info("============================================================");
    }

    @AfterEach
    void tearDown() {
        log.info("============================================================");
        log.info("@AfterEach - executed after each test method.");
    }

    private static Long parse(String feString) {
        if (Character.isUpperCase(feString.charAt(feString.length() - 1))) {
            LocalDateTime start = LocalDateTime.now();
            Period period = Period.parse("P" + feString);
            LocalDateTime end = start.plus(period);
            return start.until(end, ChronoUnit.MILLIS);
        } else {
            return Duration.parse("PT" + feString).toMillis();
        }
    }

    private boolean consistencyChecker(String instrument, String period, JsonArray candles, JsonArray trades) {
        log.info("Run consistency Checker: " + instrument + "(" + period + ")");

        boolean isPass = true;
        // input check
        if (Objects.isNull(instrument) || Objects.isNull(period) || Objects.isNull(candles) || Objects.isNull(trades)) {
            log.error("Incomplete parameter, instrument: " + instrument + ", period: " + period
                            + ", candles: " + (candles == null ? null : candles.encode())
                            + ", trades: " + (trades == null ? null : trades.encode())
                    , new IOException());
            return false;
        }

        //translate period from string to millisecond
        Long periodMillis = parse(period);

        // copy trans to List for sorting by timestamp
        List<JsonObject> tradesList = new ArrayList<>();
        for (int i = 0; i < trades.size(); i++) tradesList.add(trades.getJsonObject(i));
        tradesList.sort((a, b) -> {
            Long tA = a.getLong("d");
            Long tB = b.getLong("d");
            return tA.compareTo(tB);
        });

        JsonObject candle;
        long begin, end, timestamp;
        BigDecimal candleOpen, candleClose, candleHigh, candleLow, candleVolume;
        BigDecimal tradeOpen, tradeClose, tradeHigh, tradeLow, tradeVolume;
        BigDecimal tradePrice, tradeQuantity;
        boolean isCandlePass;
        String caseName;
        for (Object cObj : candles) {
            // get candle info
            candle = (JsonObject) cObj;

            // get period
            begin = candle.getLong("t");
            end = begin + periodMillis;
            caseName = "Case (" + begin + "-" + end + ")";

            //get other info
            candleOpen = BigDecimal.valueOf(candle.getDouble("o"));
            candleClose = BigDecimal.valueOf(candle.getDouble("c"));
            candleHigh = BigDecimal.valueOf(candle.getDouble("h"));
            candleLow = BigDecimal.valueOf(candle.getDouble("l"));
            candleVolume = BigDecimal.valueOf(candle.getDouble("v"));

            isCandlePass = true;

            // init O,C,H,L for this period
            tradeOpen = null;
            tradeClose = null;
            tradeHigh = BigDecimal.valueOf(Double.MIN_VALUE);
            tradeLow = BigDecimal.valueOf(Double.MAX_VALUE);
            tradeVolume = new BigDecimal(0);

            // iterator to get the trade between in the time period
            for (JsonObject trade : tradesList) {
                timestamp = trade.getLong("t");
                tradePrice = BigDecimal.valueOf(trade.getDouble("p"));
                tradeQuantity = BigDecimal.valueOf(trade.getDouble("q"));

                if (timestamp > begin && timestamp <= end) {
                    // this trade is in the period, set O,C,H,L
                    if (Objects.isNull(tradeOpen)) tradeOpen = tradePrice;
                    tradeClose = tradePrice;
                    tradeHigh = tradeHigh.max(tradePrice);
                    tradeLow = tradeLow.min(tradePrice);
                    tradeVolume = tradeVolume.add(tradeQuantity);
                }
            }

            // Verify O,C,H,L,V with candle data
            if (tradeVolume.compareTo(candleVolume) != 0) {
                log.error(caseName + ": volume doesn't match: " + tradeVolume + " (expected: " + candleVolume + ")");
                isCandlePass = false;
            } else {
                StringJoiner misMatch = new StringJoiner(", ");
                if (Objects.nonNull(tradeOpen) && tradeOpen.compareTo(candleOpen) != 0)
                    misMatch.add("open doesn't match: " + tradeOpen + " (expected: " + candleOpen + ")");
                if (Objects.nonNull(tradeClose) && tradeClose.compareTo(candleClose) != 0)
                    misMatch.add("close doesn't match: " + tradeClose + " (expected: " + candleClose + ")");
                if (tradeHigh.compareTo(candleHigh) != 0)
                    misMatch.add("high doesn't match: " + tradeHigh + " (expected: " + candleHigh + ")");
                if (tradeLow.compareTo(candleLow) != 0)
                    misMatch.add("low doesn't match: " + tradeLow + " (expected: " + candleLow + ")");
                if (misMatch.length() > 0) {
                    log.error(caseName + ": " + misMatch);
                    isCandlePass = false;
                }
            }

            if (isCandlePass) {
                log.info(caseName + ": PASS");
            } else {
                isPass = false;
            }
        }

        return isPass;
    }

    private Future<Void> consistencyTest(String instrument, String period) {
        Promise<Void> promise = Promise.promise();

        log.info("Start Consistency Test: " + instrument + "(" + period + ")");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("instrument_name", instrument);
        parameters.put("timeframe", period);

        //loop to call trade api to get complete trade data
        Promise<Void> tradeFuture = Promise.promise();
        Map<Long, JsonObject> tradesMap = new HashMap<>(); //store trade data in map to avoid duplication
        AtomicInteger counter = new AtomicInteger(0);
        long rounds = Math.min(parse(period) / 2000, 10);

        //set timer, run every 1 sec, call trade api
        vertx.setPeriodic(2000, timeID ->
                HttpClient.getTrades(vertx, apiInfo, parameters)
                        .onFailure(trades -> {
                            log.error("Get trade api error(" + counter + "/" + rounds + ")", trades);
                            tradeFuture.fail(trades);
                            vertx.cancelTimer(timeID);
                        })
                        .onSuccess(trades -> {
                            JsonArray data = trades
                                    .getJsonObject("result", new JsonObject())
                                    .getJsonArray("data", new JsonArray());

                            for (Object obj : data) {
                                JsonObject trade = (JsonObject) obj;
                                Long id = trade.getLong("d");
                                if (!tradesMap.containsKey(id)) tradesMap.put(id, trade);
                            }

                            if (counter.addAndGet(1) >= rounds) {
                                tradeFuture.complete();
                                vertx.cancelTimer(timeID);
                            }
                        })
        );

        //after all trade api response
        tradeFuture.future()
                .compose(tradesAr -> HttpClient.getCandleStick(vertx, apiInfo, parameters)) //get candlestick data
                .onFailure(promise::fail)
                .onSuccess(candlesAr -> {
                    //copy trades from map into json array
                    JsonArray trades = new JsonArray();
                    for (Map.Entry<Long, JsonObject> entry : tradesMap.entrySet()) trades.add(entry.getValue());

                    //parse candlestick response to get "data"
                    JsonArray candles = candlesAr.getJsonObject("result", new JsonObject()).getJsonArray("data", new JsonArray());

                    log.info("Trades response: " + trades.encode());
                    log.info("Candles response: " + candles.encode());

                    //run consistency checker to map candlesticks and trades
                    if (consistencyChecker(instrument, period, candles, trades))
                        promise.complete(); //consistency test PASS
                    else
                        promise.fail("consistency checker FAIL"); //consistency test FAIL
                });

        return promise.future();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1m", "5m", "15m", "30m", "1h", "4h", "6h", "12h", "1D", "7D", "14D", "1M"})
    public void LUNA_USDTConsistencyTest(String period) throws TimeoutException, InterruptedException {
        AtomicBoolean result = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        String instrument = "LUNA_USDT";
        consistencyTest(instrument, period)
                .onFailure(ar -> {
                    log.info(instrument + "(" + period + ")" + " consistency test: FAIL", ar);
                    latch.countDown();
                })
                .onSuccess(ar -> {
                    log.info(instrument + "(" + period + ")" + " consistency test: PASS");
                    result.compareAndSet(false, true);
                    latch.countDown();
                });

        //set testcase timeout 10min
        if (latch.await(600, TimeUnit.SECONDS)) {
            log.info("End Consistency Test: " + instrument + "(" + period + ")");
            assertTrue(result.get());
        } else {
            String errMsg = instrument + " consistency test: fail due to timeout exception.";
            log.error(errMsg);
            log.info("End Consistency Test: " + instrument + "(" + period + ")");
            throw new TimeoutException(errMsg);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1m", "5m", "15m", "30m", "1h", "4h", "6h", "12h", "1D", "7D", "14D", "1M"})
    public void ETH_CROConsistencyTest(String period) throws TimeoutException, InterruptedException {
        AtomicBoolean result = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        String instrument = "ETH_CRO";
        consistencyTest(instrument, period)
                .onFailure(ar -> {
                    log.info(instrument + "(" + period + ")" + " consistency test: FAIL", ar);
                    latch.countDown();
                })
                .onSuccess(ar -> {
                    log.info(instrument + "(" + period + ")" + " consistency test: PASS");
                    result.compareAndSet(false, true);
                    latch.countDown();
                });

        //set testcase timeout 10min
        if (latch.await(600, TimeUnit.SECONDS)) {
            log.info("End Consistency Test: " + instrument + "(" + period + ")");
            assertTrue(result.get());
        } else {
            String errMsg = instrument + " consistency test: fail due to timeout exception.";
            log.error(errMsg);
            log.info("End Consistency Test: " + instrument + "(" + period + ")");
            throw new TimeoutException(errMsg);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1m", "5m", "15m", "30m", "1h", "4h", "6h", "12h", "1D", "7D", "14D", "1M"})
    public void BTC_USDTConsistencyTest(String period) throws TimeoutException, InterruptedException {
        AtomicBoolean result = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        String instrument = "BTC_USDT";
        consistencyTest(instrument, period)
                .onFailure(ar -> {
                    log.info(instrument + "(" + period + ")" + " consistency test: FAIL", ar);
                    latch.countDown();
                })
                .onSuccess(ar -> {
                    log.info(instrument + "(" + period + ")" + " consistency test: PASS");
                    result.compareAndSet(false, true);
                    latch.countDown();
                });

        //set testcase timeout 10min
        if (latch.await(600, TimeUnit.SECONDS)) {
            log.info("End Consistency Test: " + instrument + "(" + period + ")");
            assertTrue(result.get());
        } else {
            String errMsg = instrument + " consistency test: fail due to timeout exception.";
            log.error(errMsg);
            log.info("End Consistency Test: " + instrument + "(" + period + ")");
            throw new TimeoutException(errMsg);
        }
    }
}
