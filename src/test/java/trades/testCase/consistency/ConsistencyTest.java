package trades.testCase.consistency;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.junit.jupiter.api.*;
import trades.util.HttpClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static trades.util.Config.loadConfig;

public class ConsistencyTest {
    private final static Logger log = LoggerFactory.getLogger(ConsistencyTest.class);

    private static final Map<String, Long> periodMap = new HashMap<>();

    public static Vertx vertx;
    public static Map<String, JsonObject> apiInfo;

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

    @BeforeAll
    static void setup() {
        log.info("@BeforeAll - executes once before all test methods in this class");

        vertx = Vertx.vertx();

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

        JsonObject api = apiInfo.get("getCandleStick");
        JsonArray params = api.getJsonArray("parameter");
        for (Object obj : params) {
            JsonObject param = (JsonObject) obj;
            String name = param.getString("name", null);
            if (Objects.nonNull(name) && name.equals("timeframe")) {
                JsonArray values = param.getJsonArray("value");
                for (Object value : values) {
                    periodMap.put((String) value, parse((String) value));
                }
                break;
            }
        }
    }

    @BeforeEach
    void init() {
        log.info("@BeforeEach - executes before each test method in this class");
    }

    @AfterEach
    void tearDown() {
        log.info("@AfterEach - executed after each test method.");
    }

    @AfterAll
    public static void done() {
        log.info("@AfterAll - executed after all test methods.");
        vertx.close();
    }

    private boolean consistencyChecker(JsonObject candles, JsonObject trades, Map<String, String> parameters) {
        String instrument = parameters.get("instrument_name");
        String period = parameters.get("timeframe");
        Long periodMillis = periodMap.get(period);

        JsonArray tradesResult = trades.getJsonObject("result", new JsonObject()).getJsonArray("data");
        JsonObject candlesResult = candles.getJsonObject("result");
        if (!instrument.equals(candlesResult.getString("instrument_name"))) {
            log.error("Instrument doesn't match.");
            return false;
        }

        if (!period.equals(candlesResult.getString("interval"))) {
            log.error("Timeframe doesn't match.");
            return false;
        }

        JsonObject candle, trade;
        Long begin, end, timestamp;
        Double open, close, high, low, price;
        String iName;
        JsonArray data = candlesResult.getJsonArray("data");
        for (Object cObj : data) {
            candle = (JsonObject) cObj;
            end = candle.getLong("t");
            begin = end - periodMillis;
            log.debug("Time period: " + begin + " - " + end);

            // init O,C,H,L for this period
            open = null;
            close = null;
            high = Double.MIN_VALUE;
            low = Double.MAX_VALUE;

            // iterator to get the trade between in the time period
            for (Object tObj : tradesResult) {
                trade = (JsonObject) tObj;
                timestamp = trade.getLong("t");
                iName = trade.getString("i");
                price = trade.getDouble("p");


                if (timestamp > begin && timestamp <= end && instrument.equals(iName)) {
                    log.debug("Timestamp: " + timestamp);
                    // this trade is in the period, set O,C,H,L
                    if (Objects.isNull(open)) open = price;
                    close = price;
                    high = Math.max(high, price);
                    low = Math.min(low, price);
                }
            }

            // Verify O,C,H,L with candle data
            if (Objects.isNull(open)) {
                log.debug("No timestamp in the period: " + begin + " - " + end);
                continue;
            }

            if (Objects.isNull(open) || !open.equals(candle.getDouble("o"))) {
                log.debug("open doesn't match: " + open);
                return false;
            }
            if (!close.equals(candle.getDouble("c"))) {
                log.debug("close doesn't match: " + close);
                return false;
            }
            if (!high.equals(candle.getDouble("h"))) {
                log.debug("high doesn't match: " + high);
                return false;
            }
            if (!low.equals(candle.getDouble("l"))) {
                log.debug("low doesn't match: " + low);
                return false;
            }
        }

        return true;
    }

    @Test
    public void testConsistency1m() throws TimeoutException, InterruptedException {
        AtomicBoolean result = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("instrument_name", "BTC_USDT");
        parameters.put("timeframe", "1m");

        CompositeFuture.join(HttpClient.getCandleStick(vertx, apiInfo, parameters), HttpClient.getTrades(vertx, apiInfo, parameters))
                .onFailure(ar -> {
                    log.error("get error", ar);
                    latch.countDown();
                })
                .onSuccess(ar -> {
                    JsonObject candleStickRes = ar.resultAt(0);
                    JsonObject tradesRes = ar.resultAt(1);
                    if (consistencyChecker(candleStickRes, tradesRes, parameters)) {
                        log.info("success, candleStick: " + candleStickRes.encode() + ", trades: " + tradesRes.encode());
                        result.compareAndSet(false, true);
                    } else {
                        log.info("fail, candleStick: " + candleStickRes.encode() + ", trades: " + tradesRes.encode());
                    }
                    latch.countDown();
                });

        if (latch.await(60, TimeUnit.SECONDS)) {
            assertTrue(result.get());
        } else {
            throw new TimeoutException("Request timeout by fetching event data");
        }
    }
}
