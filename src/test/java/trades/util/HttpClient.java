package trades.util;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.util.Map;
import java.util.Objects;

public class HttpClient {
    private final static Logger log = LoggerFactory.getLogger(HttpClient.class);

    private static Future<HttpResponse<Buffer>> getResponse(
            Vertx vertx, String domain, String path, Integer port, String method, Map<String, String> headers, Map<String, String> parameters, Buffer body
    ) {
        Promise<HttpResponse<Buffer>> promise = Promise.promise();

        try {
            WebClient client = WebClient.create(vertx);
            HttpRequest<Buffer> req;

            switch (method.toLowerCase()) {
                case "get":
                    req = client.get(port, domain, path);
                    break;
                case "put":
                    req = client.put(port, domain, path);
                    break;
                case "post":
                    req = client.post(port, domain, path);
                    break;
                default:
                    promise.fail("Not verified method: " + method);
                    return promise.future();
            }

            // add headers to the request
            if (Objects.nonNull(headers)) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    req.putHeader(header.getKey(), header.getValue());
                }
            }

            // add parameters to the request
            if (Objects.nonNull(parameters)) {
                for (Map.Entry<String, String> parameter : parameters.entrySet()) {
                    req.addQueryParam(parameter.getKey(), parameter.getValue());
                }
            }

            // add body to the request
            if (Objects.nonNull(body)) {
                req.sendBuffer(body);
            }

            // send request
            req.send()
                    .onFailure(promise::fail)
                    .onSuccess(promise::complete);

        } catch (Exception e) {
            promise.tryFail(e);
        }

        return promise.future();
    }

    public static Future<JsonObject> getCandleStick(Vertx vertx, Map<String, JsonObject> apiInfo, Map<String, String> parameters) {
        Promise<JsonObject> promise = Promise.promise();

        JsonObject api = apiInfo.get("getCandleStick");
        String domain = api.getString("domain");
        String path = api.getString("path");

        log.info("Query CandleStick API with domain: " + domain +", path: "+ path);
        getResponse(vertx, domain, path, 80, "get", null, parameters, null)
                .onFailure(promise::fail)
                .onSuccess(res -> {
                    int statusCode = res.statusCode();
                    if (statusCode != 200) {
                        log.error("Query CandleStick API: fail");
                        promise.fail("http status is not 200: " + statusCode + ", errorMsg: " + res.bodyAsString());
                    } else {
                        log.info("Query CandleStick API: success");
                        promise.complete(res.bodyAsJsonObject());
                    }
                });

        return promise.future();
    }

    public static Future<JsonObject> getTrades(Vertx vertx, Map<String, JsonObject> apiInfo, Map<String, String> parameters) {
        Promise<JsonObject> promise = Promise.promise();

        JsonObject api = apiInfo.get("getTrades");
        String domain = api.getString("domain");
        String path = api.getString("path");

        log.info("Query Trades API domain: " + domain +", path: "+ path);
        getResponse(vertx, domain, path, 80, "get", null, parameters, null)
                .onFailure(promise::fail)
                .onSuccess(res -> {
                    int statusCode = res.statusCode();
                    if (statusCode != 200) {
                        promise.fail("http status is not 200: " + statusCode + ", errorMsg: " + res.bodyAsString());
                    } else {
                        promise.complete(res.bodyAsJsonObject());
                    }
                });

        return promise.future();
    }
}
