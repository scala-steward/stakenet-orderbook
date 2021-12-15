package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.repositories.channels.{ChannelsPostgresRepository, ChannelsRepository}
import io.stakenet.orderbook.repositories.clients.{ClientsPostgresRepository, ClientsRepository}
import io.stakenet.orderbook.repositories.currencyPrices.{CurrencyPricesPostgresRepository, CurrencyPricesRepository}
import io.stakenet.orderbook.repositories.feeRefunds.{FeeRefundsPostgresRepository, FeeRefundsRepository}
import io.stakenet.orderbook.repositories.fees.{FeesPostgresRepository, FeesRepository}
import io.stakenet.orderbook.repositories.ipCountryCodes.{IPCountryCodesPostgresRepository, IPCountryCodesRepository}
import io.stakenet.orderbook.repositories.liquidityProviders.{
  LiquidityProvidersPostgresRepository,
  LiquidityProvidersRepository
}
import io.stakenet.orderbook.repositories.makerPayments.{MakerPaymentsPostgresRepository, MakerPaymentsRepository}
import io.stakenet.orderbook.repositories.preimages.{PreimagesPostgresRepository, PreimagesRepository}
import io.stakenet.orderbook.repositories.reports.{ReportsPostgresRepository, ReportsRepository}
import io.stakenet.orderbook.repositories.trades.{TradesPostgresRepository, TradesRepository}

class RepositoriesModule extends AbstractModule {
  override def configure(): Unit = {
    val _ = (
      bind(classOf[TradesRepository.Blocking]).to(classOf[TradesPostgresRepository]),
      bind(classOf[FeesRepository.Blocking]).to(classOf[FeesPostgresRepository]),
      bind(classOf[ChannelsRepository.Blocking]).to(classOf[ChannelsPostgresRepository]),
      bind(classOf[FeeRefundsRepository.Blocking]).to(classOf[FeeRefundsPostgresRepository]),
      bind(classOf[ReportsRepository.Blocking]).to(classOf[ReportsPostgresRepository]),
      bind(classOf[ClientsRepository.Blocking]).to(classOf[ClientsPostgresRepository]),
      bind(classOf[CurrencyPricesRepository.Blocking]).to(classOf[CurrencyPricesPostgresRepository]),
      bind(classOf[IPCountryCodesRepository.Blocking]).to(classOf[IPCountryCodesPostgresRepository]),
      bind(classOf[MakerPaymentsRepository.Blocking]).to(classOf[MakerPaymentsPostgresRepository]),
      bind(classOf[PreimagesRepository.Blocking]).to(classOf[PreimagesPostgresRepository]),
      bind(classOf[LiquidityProvidersRepository.Blocking]).to(classOf[LiquidityProvidersPostgresRepository])
    )
  }
}
