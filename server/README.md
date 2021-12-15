# Stakenet orderbook

## Debug client
You can easily connect to the orderbook server and start issuing commands easily, run `sbt console` and call `prodWs.send(command)` or `devWs.send(command)`, for example: `prodWs.send(Command.Subscribe(TradingPair.XSN_BTC))`.

Another way is to just update and run the [OrderbookClientTest.scala](app/OrderbookClientTest.scala).

## Connext
Some currencies(WETH, USDT, etc.) need a connext node running to work, check the official [docs](https://docs.connext.network/configuration) for running it, this is an example config usually saved as `node.config.json` (be sure to set the proper ETH node address):

```json
{
  "adminToken": "replacemewhenyougetsomethingproperlysecure",
  "chainProviders": {
    "1": "http://localhost:8545"
  },
  "logLevel": "info",
  "messagingUrl": "https://messaging.connext.network",
  "production": true
}
```

Once connext-node is running, you need to generate public identifiers for each currency depending on it, which can be done with:
- `curl -X POST 10.136.12.87:8000/node -d '{"index": 0}' -H "Content-type: application/json"` (use different index for different currencies).

