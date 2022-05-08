# ReconciliationTest

### IOTest 
#### Response Test
1. without instrument_name
2. instrument_name = "BTC_USDT"
3. instrument_name = "ETH_CRO"
4. instrument_name = "ICX_CRO"
5. instrument_name = "VET_USDT"
6. instrument_name = "XTZ_CRO"
7. instrument_name = "XTZ_USDT"
8. instrument_name = "EOS_USDT"
9. instrument_name = "ETH_USDT"
10. instrument_name = "BCH_USDT"
11. instrument_name = "USDC_USDT"
12. instrument_name = "ATOM_USDT"
####  Unexpected input Test
1. instrument_name = ""
2. instrument_name = null
3. instrument_name = "~!@#$%^*()_: &"
4. instrument_nam = "ATOM_USDT"

### Consistency
1. instrument_name = "BTC_USDT" & interval = "1m"
   * Verify O == the first trade
   * Verify C == the last trade
   * Verify H == the highest price
   * Verify L == the lowest price
2. other setup ...