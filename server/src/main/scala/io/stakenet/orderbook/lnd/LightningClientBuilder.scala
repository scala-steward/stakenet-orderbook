package io.stakenet.orderbook.lnd

import java.io.File

import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.{GrpcSslContexts, NettyChannelBuilder}
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext
import io.stakenet.orderbook.models.Currency
import javax.inject.Inject
import lnrpc.rpc.LightningGrpc
import org.lightningj.lnd.wrapper.{MacaroonClientInterceptor, StaticFileMacaroonContext}
import verrpc.verrpc.VersionerGrpc
import routerrpc.router.RouterGrpc

class LightningClientBuilder @Inject()(configBuilder: LightningConfigBuilder) {

  private var cache: Map[Currency, LightningGrpc.Lightning] = Map.empty

  def getLnd(currency: Currency): LightningGrpc.Lightning = this.synchronized {
    val client = cache.getOrElse(currency, buildLndClient(currency))
    cache = cache.updated(currency, client)
    client
  }

  private var versionerCache: Map[Currency, VersionerGrpc.Versioner] = Map.empty

  def getLndVersioner(currency: Currency): VersionerGrpc.Versioner = {
    val maybe = versionerCache.get(currency)
    maybe.getOrElse {
      this.synchronized {
        val client = versionerCache.getOrElse(currency, buildVersionerClient(currency))
        versionerCache = versionerCache.updated(currency, client)
        client
      }
    }
  }

  private var routerCache: Map[Currency, RouterGrpc.Router] = Map.empty

  def getLndRouter(currency: Currency): RouterGrpc.Router = this.synchronized {
    val client = routerCache.getOrElse(currency, buildRouterClient(currency))
    routerCache = routerCache.updated(currency, client)
    client
  }

  def getMinSize(currency: Currency): Long = {
    val lndConfig = configBuilder.getConfig(currency)
    lndConfig.channelMinSize
  }

  def getMaxSatPerByte(currency: Currency): Long = {
    val lndConfig = configBuilder.getConfig(currency)
    lndConfig.maxSatPerByte
  }

  def getPublicKey(currency: Currency): String = {
    val lndConfig = configBuilder.getConfig(currency)
    lndConfig.publicKey
  }

  def getInvoiceUsdLimit(currency: Currency): BigDecimal = {
    val lndConfig = configBuilder.getConfig(currency)
    lndConfig.invoiceUsdLimit
  }

  private def buildLndClient(currency: Currency): LightningGrpc.Lightning = {
    val lndConfig = configBuilder.getConfig(currency)
    val sslContext = gRPCSSLContext(lndConfig.tlsCertificateFile)
    val macaroonClientInterceptor: MacaroonClientInterceptor = macaroonInterceptor(lndConfig.macaroonFile)
    val channel = managedChannel(sslContext, lndConfig.host, lndConfig.port, macaroonClientInterceptor)
    lightningStub(channel)
  }

  private def buildVersionerClient(currency: Currency): VersionerGrpc.Versioner = {
    val lndConfig = configBuilder.getConfig(currency)
    val sslContext = gRPCSSLContext(lndConfig.tlsCertificateFile)
    val macaroonClientInterceptor: MacaroonClientInterceptor = macaroonInterceptor(lndConfig.macaroonFile)
    val channel = managedChannel(sslContext, lndConfig.host, lndConfig.port, macaroonClientInterceptor)
    versionerStub(channel)
  }

  private def buildRouterClient(currency: Currency): RouterGrpc.Router = {
    val lndConfig = configBuilder.getConfig(currency)
    val sslContext = gRPCSSLContext(lndConfig.tlsCertificateFile)
    val macaroonClientInterceptor: MacaroonClientInterceptor = macaroonInterceptor(lndConfig.macaroonFile)
    val channel = managedChannel(sslContext, lndConfig.host, lndConfig.port, macaroonClientInterceptor)
    routerStub(channel)
  }

  private def gRPCSSLContext(filename: String): SslContext = {
    val trustedServerCertificate = getClass.getResourceAsStream(filename)
    GrpcSslContexts
      .forClient()
      .trustManager(trustedServerCertificate)
      .build()
  }

  private def macaroonInterceptor(macaroonPath: String): MacaroonClientInterceptor = {
    val macaroonUrl = getClass.getResource(macaroonPath).getPath
    val macaroonFile = new File(macaroonUrl)
    val macaroonContext = new StaticFileMacaroonContext(macaroonFile)
    new MacaroonClientInterceptor(macaroonContext)
  }

  private def managedChannel(
      sslContext: SslContext,
      host: String,
      port: Int,
      macaroonClientInterceptor: MacaroonClientInterceptor
  ): ManagedChannel = {
    NettyChannelBuilder
      .forAddress(host, port)
      .sslContext(sslContext)
      .intercept(macaroonClientInterceptor)
      .build()
  }

  private def lightningStub(
      managedChannel: ManagedChannel
  ): LightningGrpc.Lightning = {
    sys.addShutdownHook {
      val _ = managedChannel.shutdown()
    }
    LightningGrpc.stub(managedChannel)
  }

  private def versionerStub(
      managedChannel: ManagedChannel
  ): VersionerGrpc.Versioner = {
    sys.addShutdownHook {
      val _ = managedChannel.shutdown()
    }
    VersionerGrpc.stub(managedChannel)
  }

  private def routerStub(managedChannel: ManagedChannel): RouterGrpc.Router = {
    sys.addShutdownHook {
      val _ = managedChannel.shutdown()
    }
    RouterGrpc.stub(managedChannel)
  }
}
