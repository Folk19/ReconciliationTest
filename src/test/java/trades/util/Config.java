package trades.util;

import io.vertx.core.json.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.stream.Collectors;

public class Config {
    public static JsonObject loadConfig(String filename) {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream(filename);
        if (Objects.isNull(inputStream)) {
            return null;
        }

        String result = new BufferedReader(new InputStreamReader(inputStream))
                .lines()
                .collect(Collectors.joining("\n"));

        return new JsonObject(result);
    }
}
