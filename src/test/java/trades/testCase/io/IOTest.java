package trades.testCase.io;

import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.*;
import trades.util.HttpClient;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static trades.util.Config.loadConfig;

public class IOTest {
    private final static Logger log = LoggerFactory.getLogger(IOTest.class);

    private static Set<String> sideList = new HashSet<>();
    private static Set<String> instrumentList = new HashSet<>();

    public static Vertx vertx;
    public static Map<String, JsonObject> apiInfo;

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

        sideList.add("buy");
        sideList.add("sell");

        JsonObject api = apiInfo.get("getTrades");
        JsonArray params = api.getJsonArray("parameter");
        for (Object obj : params) {
            JsonObject param = (JsonObject) obj;
            String name = param.getString("name", null);
            if (Objects.nonNull(name) && name.equals("instrument_name")) {
                JsonArray values = param.getJsonArray("value");
                for (Object value : values) {
                    instrumentList.add((String) value);
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

    private boolean tradesResponseFormatChecker(JsonObject res) {
        try {
            Integer code = res.getInteger("code", null);
            String method = res.getString("method", null);
            JsonArray result = res.getJsonArray("result", null);
            if (Objects.isNull(code) || Objects.isNull(method) || Objects.isNull(result)) return false;

            JsonObject _trade;
            Double price, quantity;
            Integer id, timestamp;
            String side, instrument;
            for (Object trade : result) {
                _trade = (JsonObject) trade;
                id = _trade.getInteger("d", null);
                side = _trade.getString("s", null);
                price = _trade.getDouble("p", null);
                quantity = _trade.getDouble("q", null);
                timestamp = _trade.getInteger("t", null);
                instrument = _trade.getString("i", null);
                if (Objects.isNull(id) || Objects.isNull(side) || Objects.isNull(price)
                        || Objects.isNull(quantity) || Objects.isNull(timestamp) || Objects.isNull(instrument)) {
                    return false;
                }
            }
        } catch (ClassCastException e) {
            log.error("Trades response format parsing error", e);
            return false;
        }

        log.info("Response format checker: success");
        return true;
    }

    private boolean tradesResponseContentChecker(JsonObject res) {
        Integer code = res.getInteger("code", null);
        String method = res.getString("method", null);
        JsonArray result = res.getJsonArray("method", null);

        if (Objects.isNull(code) || !code.equals(0)) return false;
        if (Objects.isNull(method) || !method.equals("public/get-trades")) return false;
        if (Objects.isNull(result)) return false;

        JsonObject trade;
        String side, instrument;
        for (Object obj : result) {
            trade = (JsonObject) obj;
            side = trade.getString("s", null);
            instrument = trade.getString("i", null);

            if (Objects.isNull(side) || !sideList.contains(side)) return false;
            if (Objects.isNull(instrument) || !instrumentList.contains(instrument)) return false;
        }

        log.info("Response content checker: success");
        return true;
    }

    @Test
    public void testBTC_USDT() throws TimeoutException, InterruptedException {
        AtomicBoolean result = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("instrument_name", "BTC_USDT");

        HttpClient.getTrades(vertx, apiInfo, 80, "get", null, parameters, null)
                .onFailure(ar -> {
                    log.error("get error", ar);
                    latch.countDown();
                })
                .onSuccess(ar -> {
                    if (tradesResponseFormatChecker(ar) && tradesResponseContentChecker(ar)) {
                        result.compareAndSet(false, true);
                        log.info("success: " + ar.encode());
                    } else {
                        log.error("Response checker fail: " + ar.encode());
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
