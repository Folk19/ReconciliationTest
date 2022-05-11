# [Crypto.com] Trade API Reconciliation Unit Test

### Introduction
Unit Test for verifiing the consistency of Trade API (https://exchange-docs.crypto.com/#public-get-trades) response with Candl Stick API (https://exchange-docs.crypto.com/#public-get-candlestick).

### Design
##### Test Design
1. loop to get the response of Trade API to collect more trade data
    * send request per 2 sec, with max 10 times.
2. get the response of Candle Stick API
3. Sort trade data by ID
4. Iterate the candlestick timestamp and calculate the period
5. Iterate the trade date to get the trade between the period and get the O, C, H, and L value
6. Check if the total number of trade volumes equals the candlestick volume
7. Compare the O,C,H,L value to candlestick O,C,H,L value

##### Code Struct
1. Test API by instrument name with all periods.
2. API config is in `resources/config.json`
3. Invoke library: Vert.x
4. Using BigDecimal to compare floating-point

##### Others
1. The Production Trade API can not get at least one candlestick trade, even with a loop to get, the volume is still not matched, so I go with UAT Sandbox as the {url}.
2. While using UAT Sandbox, I found the timestamp of the response of Candle Stick API is not the end time of the candlestick, but the start time of the candlestick. However, I couldn't verify this on production API due to point 1.

### Env
* OS: Windows 10
* IDE: IntelliJ IDEA 2021.2.3
* SDK: JDK11
* Project management: Maven

### Installation
1. open project with IDE, select Maven project
##### Run all test
1. open file `trade-api-unit-test/src/test/java/trades/TestSuite.java`
2. Run all test
##### Run Instrument Test
1. open file `trade-api-unit-test/src/test/java/trades/testCase/consistency/ConsistencyTest.java`
2. Run one instrument test

### Unit Test
#### TestSuite
1. BTC_USDT Consistency Test 
2. ETH_CRO Consistency Test
3. LUNA_USDT Consistency Test
#### BTC_USDT Consistency Test Case
|instrument_name|timeframe|
|---------------|---------|
|BTC_USDT|1m|
|BTC_USDT|5m|
|BTC_USDT|15m|
|BTC_USDT|30m|
|BTC_USDT|1h|
|BTC_USDT|4h|
|BTC_USDT|6h|
|BTC_USDT|12h|
|BTC_USDT|1D|
|BTC_USDT|7D|
|BTC_USDT|14D|
|BTC_USDT|1M|
#### ETH_CRO Consistency Test Case
|instrument_name|timeframe|
|---------------|---------|
|ETH_CRO|1m|
|ETH_CRO|5m|
|ETH_CRO|15m|
|ETH_CRO|30m|
|ETH_CRO|1h|
|ETH_CRO|4h|
|ETH_CRO|6h|
|ETH_CRO|12h|
|ETH_CRO|1D|
|ETH_CRO|7D|
|ETH_CRO|14D|
|ETH_CRO|1M|
#### LUNA_USDT Consistency Test Case
|instrument_name|timeframe|
|---------------|---------|
|LUNA_USDT|1m|
|LUNA_USDT|5m|
|LUNA_USDT|15m|
|LUNA_USDT|30m|
|LUNA_USDT|1h|
|LUNA_USDT|4h|
|LUNA_USDT|6h|
|LUNA_USDT|12h|
|LUNA_USDT|1D|
|LUNA_USDT|7D|
|LUNA_USDT|14D|
|LUNA_USDT|1M|