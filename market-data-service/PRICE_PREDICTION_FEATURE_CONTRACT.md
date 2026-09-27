# Market Data → Price Prediction contract

The Price Prediction model currently uses Hugging Face artifact revision
`ef240954a295fc87de033ca853be215b567d53f8` from
`andreamena/price-prediction-lstm-stock-news`. Its `metadata.json` specifies
the ordered price feature vector:

1. `Open`, `High`, `Low`, `Close` — price units.
2. `Volume` — broker bar volume.
3. `Return` — fractional close-to-close return (`0.012` means +1.2%).
4. `MA_5`, `MA_20` — moving averages in price units.
5. `Volatility_5` — sample standard deviation of the last five fractional returns.
6. `RSI_14` — standard 0–100 RSI.
7. `MACD` — fast EMA minus slow EMA, in price units.

`MarketData` serializes these exact JSON property names in that order. The
model consumes the properties in the order stated by its metadata, so new
model versions require an explicit contract review before deployment.
